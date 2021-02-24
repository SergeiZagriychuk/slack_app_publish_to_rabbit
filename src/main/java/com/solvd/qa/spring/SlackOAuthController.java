package com.solvd.qa.spring;

import javax.servlet.annotation.WebServlet;

import com.slack.api.bolt.App;
import com.slack.api.bolt.servlet.SlackOAuthAppServlet;

@SuppressWarnings("serial")
@WebServlet(urlPatterns = { "/slack/oauth/callback", "/slack/oauth/start" })
public class SlackOAuthController extends SlackOAuthAppServlet {

    public SlackOAuthController(App app) {
        super(app);
    }

}