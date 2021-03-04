package com.solvd.qa.util;

import java.util.Map;

import com.slack.api.model.view.ViewState;

public class BuilderUtil {

	public static String extract(Map<String, Map<String, ViewState.Value>> stateValues, String blockId,
			String actionId) {
		return stateValues.get(blockId).get(actionId).getValue();
	}

}
