package com.solvd.qa.workflow.step.builder;

import static com.slack.api.model.workflow.WorkflowSteps.asStepOutputs;
import static com.slack.api.model.workflow.WorkflowSteps.stepInput;
import static com.solvd.qa.util.workflow.BuilderUtil.extract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.slack.api.bolt.middleware.builtin.WorkflowStep;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.model.view.ViewState;
import com.slack.api.model.workflow.WorkflowStepExecution;
import com.slack.api.model.workflow.WorkflowStepInput;
import com.slack.api.model.workflow.WorkflowStepOutput;
import com.solvd.qa.stf.STFStatus;
import com.solvd.qa.util.FreemarkerUtil;

import io.restassured.RestAssured;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class STFWorkflow {

	private final static String PROCESS_WEBHOOK = "process_webhook";

	private final static String KEY_TENANT = "tenant";
	private final static String KEY_WEBHOOK = "webhook";

	public WorkflowStep buildStep() {

		WorkflowStep step = WorkflowStep.builder().callbackId(PROCESS_WEBHOOK).edit((req, ctx) -> {
			log.info("Edit webhook workflow step '" + PROCESS_WEBHOOK + "' by user "
					+ req.getPayload().getUser().getUsername());
			String triggerId = req.getPayload().getTriggerId();
			com.slack.api.bolt.response.Response rs = ctx.ack();

			Properties p = new Properties();
			Map<String, WorkflowStepInput> inputs = req.getPayload().getWorkflowStep().getInputs();
			if (inputs.containsKey(KEY_TENANT)) {
				p.put("tenant_init_value", inputs.get(KEY_TENANT).getValue().toString());
			}
			if (inputs.containsKey(KEY_WEBHOOK)) {
				p.put("webhook_init_value", inputs.get(KEY_WEBHOOK).getValue().toString());
			}
			String bodyWInitValues = FreemarkerUtil.processTemplate("views/config_webhook_view.json", p);
			ViewsOpenRequest viewsOpenRequest = ViewsOpenRequest.builder().triggerId(triggerId)
					.viewAsString(bodyWInitValues).build();
			ViewsOpenResponse openResponse = ctx.client().viewsOpen(viewsOpenRequest);
			if (openResponse.getError() != null) {
				log.error("Error during view opening: " + openResponse.getError());
			}

			return rs;
		}).save((req, ctx) -> {
			log.info("Save webhook workflow step by user " + req.getPayload().getUser().getUsername());
			Map<String, Map<String, ViewState.Value>> stateValues = req.getPayload().getView().getState().getValues();
			Map<String, WorkflowStepInput> inputs = new HashMap<>();
			stateValues.keySet().stream().forEach(k -> {
				inputs.put(k, stepInput(i -> i.value(extract(stateValues, k, k))));
			});
			List<WorkflowStepOutput> outputs = asStepOutputs();
			ctx.update(inputs, outputs);
			return ctx.ack();
		}).execute((req, ctx) -> {
			log.info("Execute webhook workflow");
			WorkflowStepExecution wfStep = req.getPayload().getEvent().getWorkflowStep();
			Map<String, Object> outputs = new HashMap<>();
			wfStep.getInputs().keySet().stream().forEach(k -> {
				outputs.put(k.toString(), wfStep.getInputs().get(k).getValue());
			});
			try {
				Object tenant = outputs.get(KEY_TENANT);
				Object webhookUrl = outputs.get(KEY_WEBHOOK);
				// {status_android, status_ios}
				String[] statuses = STFStatus.getStfDevicesStatus(tenant.toString());

				Properties p = new Properties();
				p.put("status_android", statuses[0]);
				p.put("status_ios", statuses[1]);
				String webhookBody = FreemarkerUtil.processTemplate("webhook/slack_webhook_rq.json", p);

				RestAssured.given().urlEncodingEnabled(false).with().body(webhookBody).post(webhookUrl.toString())
						.then().and().assertThat().statusCode(200);

				ctx.complete(outputs);

				log.info("Webhook flow was successfully triggered");
			} catch (Exception e) {
				Map<String, Object> error = new HashMap<>();
				error.put("message", "Something wrong!" + System.lineSeparator() + e.getMessage());
				ctx.fail(error);
			}
			return ctx.ack();
		}).build();

		return step;
	}

}
