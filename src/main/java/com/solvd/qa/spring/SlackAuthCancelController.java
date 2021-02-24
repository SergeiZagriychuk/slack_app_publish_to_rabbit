package com.solvd.qa.spring;

import javax.servlet.annotation.WebServlet;

import com.slack.api.bolt.App;
import com.slack.api.bolt.WebEndpoint;
import com.slack.api.bolt.WebEndpoint.Method;
import com.slack.api.bolt.servlet.WebEndpointServlet;

@SuppressWarnings("serial")
@WebServlet(urlPatterns = { "/slack/oauth/cancellation" })
public class SlackAuthCancelController extends WebEndpointServlet {

	private final static WebEndpoint ENDPOINT = new WebEndpoint(Method.GET, "/slack/oauth/cancellation");

	public SlackAuthCancelController(App app) {
		super(ENDPOINT, app.getWebEndpointHandlers().get(ENDPOINT), app.config());
	}

	static String renderCancellationPageHtml(String queryString) {
		return "<http><body><h3 align='center'><br/><br/><br/>Error during authorization! &#128532;&#128533;&#128561;</h3></body></http>";
	}

}