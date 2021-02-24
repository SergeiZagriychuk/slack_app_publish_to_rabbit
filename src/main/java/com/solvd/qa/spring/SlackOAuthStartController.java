package com.solvd.qa.spring;

import javax.servlet.annotation.WebServlet;

import com.slack.api.bolt.App;
import com.slack.api.bolt.servlet.SlackOAuthAppServlet;

@SuppressWarnings("serial")
@WebServlet("/slack/oauth/start")
public class SlackOAuthStartController extends SlackOAuthAppServlet {

	public SlackOAuthStartController(App app) {
		super(app);
	}

}