package com.solvd.qa.util.workflow;

import java.util.Map;

import com.slack.api.model.view.ViewState;

public class BuilderUtil {

	public static String extract(Map<String, Map<String, ViewState.Value>> stateValues, String blockId,
			String actionId) {
		return stateValues.get(blockId).get(actionId).getValue();
	}

	public static boolean blockExist(Map<String, Map<String, ViewState.Value>> stateValues, String blockId) {
		return stateValues.get(blockId) != null;
	}

}
