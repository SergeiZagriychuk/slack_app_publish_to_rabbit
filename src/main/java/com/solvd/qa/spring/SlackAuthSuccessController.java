package com.solvd.qa.spring;

import javax.servlet.annotation.WebServlet;

import com.slack.api.bolt.App;
import com.slack.api.bolt.WebEndpoint;
import com.slack.api.bolt.WebEndpoint.Method;
import com.slack.api.bolt.servlet.WebEndpointServlet;

@SuppressWarnings("serial")
@WebServlet(urlPatterns = { "/slack/oauth/completion" })
public class SlackAuthSuccessController extends WebEndpointServlet {

    private final static WebEndpoint ENDPOINT = new WebEndpoint(Method.GET, "/slack/oauth/completion");

    public SlackAuthSuccessController(App app) {
        super(ENDPOINT, app.getWebEndpointHandlers().get(ENDPOINT), app.config());
    }

    static String renderCompletionPageHtml(String queryString) {
        return "<http><body><h3 align='center'><br/><br/><br/>Slack app was successfully authorized to your workspace! &#128079;&#128079;&#128079;</h3></body></http>";
    }

}