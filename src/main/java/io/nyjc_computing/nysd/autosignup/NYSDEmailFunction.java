package io.nyjc_computing.nysd.autosignup;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * This {@link HttpFunction} sends a welcome email to the student.
 * <p>
 * This class expects some environmental variables. Namely, they are:
 * <ul>
 *     <li>{@code GITHUB_OAUTH}, the GitHub personal access token of the organization.</li>
 * </ul>
 */
public class NYSDEmailFunction implements HttpFunction {

    private static final Gmail gmail = connectToGmail();
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String GAPI_APPLICATION_NAME = System.getenv("GAPI_APPLICATION_NAME");
    private static final String GAPI_EMAIL = System.getenv("GAPI_EMAIL");
    private static final List<String> GAPI_SCOPES = Collections.singletonList(GmailScopes.GMAIL_SEND);
    private static final Gson gson = new Gson();
    private static final String EMAIL_SUBJECT = "[NYSD] You're on the road to joining NYSD!";
    public static final String EMAIL_BODY = """
            <h1>You&apos;re almost there!</h1>
            <p>Dear NYJCian,</p>
            <p>
                Thank you for taking the first step on the path to being a <em>Nanyang System Developer</em>!
                You need to know 2 things to start contributing:
            </p>
            <ul>
                <li>How GitHub works, how to propose changes through a pull request</li>
                <li>Very basic Python programming</li>
            </ul>
            <h2>Our programme</h2>
            <p>
                Our tentative programme and participation tiers are described on our programme page.
                It is very barebones for now, because this is a very new programme, and you will be the pioneer batch!
                It means you have a chance to shape what it will become, to decide the kinds of projects we will take up,
                how our training programme should be developed, and finally, how we will recruit and train new members.
            </p>
            <h2>Training</h2>
            <p>
                Training materials for followers are available online.
                You can skip the training materials if you already know the content, but the assessment is mandatory.
            </p>
            <h2>Assessment</h2>
            <p>The assessment you need to complete is also described on the same page, and comprises 2 types of tasks:</p>
            <ul>
                <li>GitHub Classroom assignments are GitHub repositories that are submitted online.</li>
                <li>GitHub pull requests are made through the GitHub interface.</li>
            </ul>
            <p>
                Once we confirm your completion of these tasks, we will promote you to Contributor,
                which officially recognises you as a member of NYSD 🤩
            </p>
            <h2>Help</h2>
            <p>
                You are not expected to know everything. You are expected to use the internet, AI chatbots, and other resources to learn and understand. And if you get stuck, you are expected to ask for help in the #nanyang-system-developers channel on Discord; we’ll guide you along as best as we can, leaving enough room for you to still learn the skills you need. We all started as beginners, and there is no shame in asking about what you don’t yet know.
            </p>
            <p>
                We hold termly face-to-face briefing sessions for new members for them to find out what the programme
                is about. We also host weekly office hours on Discord, where you can share screen and ask a live person
                for help in our support voice channels. These will be announced in Discord, so do join our Discord
                server if you have yet to. See you on Discord soon, and don’t give up!
            </p>
            <p>💙, the NYSD team</p>
            """.trim();

    @SneakyThrows
    @NotNull
    private static Gmail connectToGmail() {
        String clientId = System.getenv("GAPI_CLIENT_ID");
        String clientSecret = System.getenv("GAPI_CLIENT_SECRET");
        String refreshToken = System.getenv("GAPI_REFRESH_TOKEN");
        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
        details.setClientId(clientId);
        details.setClientSecret(clientSecret);
        GoogleClientSecrets secrets = new GoogleClientSecrets();
        secrets.setWeb(details);
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                secrets,
                GAPI_SCOPES
        )
                .setDataStoreFactory(new MemoryDataStoreFactory())
                .setAccessType("offline")
                .build();
        TokenResponse tokenResponse = new GoogleTokenResponse()
                .setRefreshToken(refreshToken);
        Credential credential = flow.createAndStoreCredential(tokenResponse, "user");
        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
        )
                .setApplicationName(GAPI_APPLICATION_NAME)
                .build();
    }

    @Override
    public void service(@NotNull HttpRequest request, @NotNull HttpResponse response) throws Exception {
        FunctionHttpRequestBody body = gson.fromJson(request.getReader(), FunctionHttpRequestBody.class);
        gmail.users().messages()
                .send("me", createMessageWithEmail(createEmail(body.email())))
                .execute();
        response.setStatusCode(200);
    }

    @NotNull
    @Contract(pure = true)
    private static MimeMessage createEmail(
            String toEmailAddress
    ) throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress(NYSDEmailFunction.GAPI_EMAIL));
        email.addRecipient(
                jakarta.mail.Message.RecipientType.TO,
                new InternetAddress(toEmailAddress)
        );
        email.setSubject(NYSDEmailFunction.EMAIL_SUBJECT);
        email.setText(NYSDEmailFunction.EMAIL_BODY);
        return email;
    }

    @NotNull
    @Contract(pure = true)
    private static Message createMessageWithEmail(@NotNull MimeMessage emailContent)
            throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    public record FunctionHttpRequestBody(String email) {}

}
