package com.solvd.qa.workflow.step.builder;

import static com.slack.api.model.workflow.WorkflowSteps.asStepOutputs;
import static com.slack.api.model.workflow.WorkflowSteps.stepInput;
import static com.solvd.qa.util.workflow.BuilderUtil.extract;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import com.slack.api.bolt.middleware.builtin.WorkflowStep;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.model.view.ViewState;
import com.slack.api.model.workflow.WorkflowStepExecution;
import com.slack.api.model.workflow.WorkflowStepInput;
import com.slack.api.model.workflow.WorkflowStepOutput;
import com.solvd.qa.util.FreemarkerUtil;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZebrunnerWorkflow {

	private final static String PROCESS_WEBHOOK = "zbr_webhook";

	private final static String KEY_WEBHOOK = "webhook";
	private final static String KEY_SECRET = "secret";
	private final static String KEY_ENV_VARS = "env_vars";
	private final static String KEY_SLACK_WEBHOOK = "slack_webhook";

	public WorkflowStep buildStep() {

		WorkflowStep step = WorkflowStep.builder().callbackId(PROCESS_WEBHOOK).edit((req, ctx) -> {
			log.info("Edit webhook workflow step '" + PROCESS_WEBHOOK + "' by user "
					+ req.getPayload().getUser().getUsername());
			String triggerId = req.getPayload().getTriggerId();
			com.slack.api.bolt.response.Response rs = ctx.ack();

			Properties p = new Properties();
			Map<String, WorkflowStepInput> inputs = req.getPayload().getWorkflowStep().getInputs();
			if (inputs.containsKey(KEY_WEBHOOK)) {
				p.put("webhook_init_value", inputs.get(KEY_WEBHOOK).getValue().toString());
			}
			if (inputs.containsKey(KEY_SECRET)) {
				p.put("secret_init_value", inputs.get(KEY_SECRET).getValue().toString());
			}
			if (inputs.containsKey(KEY_ENV_VARS)) {
				p.put("env_vars_init_value", inputs.get(KEY_ENV_VARS).getValue().toString());
			}
			if (inputs.containsKey(KEY_SLACK_WEBHOOK)) {
				p.put("slack_webhook_init_value", inputs.get(KEY_SLACK_WEBHOOK).getValue().toString());
			}
			String bodyWInitValues = FreemarkerUtil.processTemplate("views/zbr_webhook_view.json", p);
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
				String val = extract(stateValues, k, k);
				if (!StringUtils.isEmpty(val)) {
					inputs.put(k, stepInput(i -> i.value(val)));
				}
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
			Object slackWebhook = null;
			try {
				Object webhookUrl = outputs.get(KEY_WEBHOOK);
				Object secret = outputs.get(KEY_SECRET);
				Object envVars = outputs.get(KEY_ENV_VARS);
				slackWebhook = outputs.get(KEY_SLACK_WEBHOOK);

				String resultLink = callWebhook(webhookUrl.toString(), secret, envVars);

				if (slackWebhook != null) {
					String runUrl = getResults(resultLink, secret);
					postSlackMessage(
							"Zebrunner launcher was successfully triggered. Monitor results by next link <a href=\""
									+ runUrl + "\">Link</a>",
							slackWebhook.toString());
				}

				ctx.complete(outputs);

				log.info("Webhook flow was successfully triggered");
			} catch (Exception e) {
				Map<String, Object> error = new HashMap<>();
				error.put("message", "Something wrong!" + System.lineSeparator() + e.getMessage());
				if (slackWebhook != null) {
					postSlackMessage("Error happened during triggering: " + e.getMessage(), slackWebhook.toString());
				}
				ctx.fail(error);
			}
			return ctx.ack();
		}).build();

		return step;
	}

	public static String encode(String secretKey, String data) throws Exception {
		Mac sha256HMAC = Mac.getInstance("HmacSHA256");
		SecretKeySpec key = new SecretKeySpec(Arrays.copyOf(Base64.decodeBase64(secretKey), 64), "HmacSHA256");
		sha256HMAC.init(key);
		return Base64.encodeBase64String(sha256HMAC.doFinal(data.getBytes("UTF-8")));
	}

	public static String callWebhook(String url, Object secret, Object envVars) throws Exception {
		RequestSpecification requestSpecification = RestAssured.given().urlEncodingEnabled(false).with()
				.contentType(ContentType.JSON);

		if (secret != null) {
			String ts = Instant.now().toString();
			String sign = encode(secret.toString(), url.concat(ts));
			requestSpecification = requestSpecification.header("x-zbr-timestamp", ts).header("x-zbr-signature", sign);
		}

		if (envVars != null) {
			String jsonEnvVars = "{ \"config\": { \"envVars\": [ %s ] } }";
			String rpl = "";
			for (String pair : envVars.toString().split(";")) {
				rpl = rpl.concat(String.format("{ \"name\": \"%s\", \"value\": \"%s\" }, ",
						StringUtils.substringBefore(pair, "="), StringUtils.substringAfter(pair, "=")));
			}
			if (StringUtils.isNotEmpty(rpl)) {
				rpl = StringUtils.removeEnd(rpl, ", ");
			}
			jsonEnvVars = String.format(jsonEnvVars, rpl);
			log.info(jsonEnvVars);

			requestSpecification = requestSpecification.body(jsonEnvVars);
		}

		String resultsLink = requestSpecification.post(url).then().and().assertThat().statusCode(202).extract()
				.jsonPath().getString("data.resultsLink");
		return resultsLink;
	}

	public static String getResults(String url, Object secret) throws Exception {
		RequestSpecification requestSpecification = RestAssured.given().urlEncodingEnabled(false).with()
				.contentType(ContentType.JSON);

		if (secret != null) {
			String ts = Instant.now().toString();
			String sign = encode(secret.toString(), url.concat(ts));
			requestSpecification = requestSpecification.header("x-zbr-timestamp", ts).header("x-zbr-signature", sign);
		}

		String resultsLink = requestSpecification.get(url).then().and().assertThat().statusCode(200).extract()
				.jsonPath().getString("_links.htmlUrl");
		return resultsLink;
	}

	public void postSlackMessage(String msg, String slackWebhook) {
		Properties p = new Properties();
		p.put("msg", msg);
		String webhookBody = FreemarkerUtil.processTemplate("webhook/slack_webhook_zbr_rq.json", p);

		RestAssured.given().urlEncodingEnabled(false).with().body(webhookBody).post(slackWebhook).then().and()
				.assertThat().statusCode(200);
	}

}
