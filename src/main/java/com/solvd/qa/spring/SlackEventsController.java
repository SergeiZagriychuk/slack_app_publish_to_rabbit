package com.solvd.qa.spring;

import javax.servlet.annotation.WebServlet;

import com.slack.api.bolt.App;
import com.slack.api.bolt.servlet.SlackAppServlet;

@SuppressWarnings("serial")
@WebServlet("/slack/events")
public class SlackEventsController extends SlackAppServlet {

	public SlackEventsController(App app) {
		super(app);
	}

}
