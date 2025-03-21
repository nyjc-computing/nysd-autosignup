package io.nyjc_computing.nysd.autosignup;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;

/**
 * This {@link HttpFunction} invites the student to the NYSD Followers
 * GitHub organization.
 * <p>
 * This class expects three environmental variables. Namely, they are:
 * <ul>
 *     <li>{@code GITHUB_TOKEN}, the GitHub personal access token of the user.</li>
 * </ul>
 * <p>
 * The request itself (which must be in JSON) expects the following:
 * <ul>
 *     <li>{@code email}, the student's NYJC email;</li>
 *     <li>{@code username}, the student's GitHub username</li>
 * </ul>
 * This is because we need to run some checks on whether the GitHub account actually
 * belongs to an NYJC student.
 */
public class GHInvitationFunction implements HttpFunction {

    private static final String org = "nysd-followers";
    private static final Gson gson = new Gson();
    @NotNull private static final String token = System.getenv("GH_TOKEN");
    private static final HttpClient client = HttpClient.newHttpClient();

    @Override
    public void service(@NotNull HttpRequest httpRequest, @NotNull HttpResponse httpResponse) throws Exception {
        FunctionHttpRequestBody body = gson.fromJson(httpRequest.getReader(), FunctionHttpRequestBody.class);
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(new URI("https://api.github.com/orgs/" + org + "/invitations"))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + body.email() + "\",\"role\":\"direct_member\"}"
                ))
                .build();
        client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        httpResponse.setStatusCode(200);
    }

    private record FunctionHttpRequestBody(String email) {}

}
