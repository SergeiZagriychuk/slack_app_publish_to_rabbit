package com.solvd.qa.spring;

import javax.servlet.annotation.WebServlet;

import com.slack.api.bolt.App;
import com.slack.api.bolt.servlet.SlackOAuthAppServlet;

@SuppressWarnings("serial")
@WebServlet("/slack/oauth/callback")
public class SlackOAuthRedirectController extends SlackOAuthAppServlet {

	public SlackOAuthRedirectController(App app) {
		super(app);
	}

}