package com.solvd.qa.workflow.step.builder;

import static com.slack.api.model.workflow.WorkflowSteps.asStepOutputs;
import static com.slack.api.model.workflow.WorkflowSteps.stepInput;
import static com.solvd.qa.workflow.step.builder.BuilderUtil.extract;
import static io.restassured.http.ContentType.JSON;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.hamcrest.Matchers;

import com.slack.api.bolt.middleware.builtin.WorkflowStep;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.model.view.ViewState;
import com.slack.api.model.workflow.WorkflowStepExecution;
import com.slack.api.model.workflow.WorkflowStepInput;
import com.slack.api.model.workflow.WorkflowStepOutput;
import com.solvd.qa.util.FileUtil;

import io.restassured.RestAssured;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JenkinsCaller {

	public static WorkflowStep buildStep() {

		WorkflowStep step = WorkflowStep.builder().callbackId("start_tests_jenkins").edit((req, ctx) -> {
			log.info("Edit workflow step 'start_tests_jenkins' by user " + req.getPayload().getUser().getUsername());
			String triggerId = req.getPayload().getTriggerId();
			com.slack.api.bolt.response.Response rs = ctx.ack();

			String viewJson = FileUtil.getResourceFileAsString("views/init_job_view.json");
			Map<String, String> values = new HashMap<String, String>();
			Map<String, WorkflowStepInput> inputs = req.getPayload().getWorkflowStep().getInputs();
			values.put("routing_key_initial_value",
					inputs.containsKey(Keys.routing_key.toString()) ? String.format(",\"initial_value\" : \"%s\"",
							inputs.get(Keys.routing_key.toString()).getValue().toString()) : "");
			values.put("repo_initial_value",
					inputs.containsKey(Keys.repo.toString()) ? String.format(",\"initial_value\" : \"%s\"",
							inputs.get(Keys.repo.toString()).getValue().toString()) : "");
			values.put("suite_initial_value",
					inputs.containsKey(Keys.suite.toString()) ? String.format(",\"initial_value\" : \"%s\"",
							inputs.get(Keys.suite.toString()).getValue().toString()) : "");
			StringBuilder extraParams = new StringBuilder();
			inputs.keySet().stream().filter(k -> !EnumUtils.isValidEnum(Keys.class, k))
					.forEach(k -> extraParams.append(k + " "));
			values.put("params_initial_value",
					extraParams.length() != 0 ? String.format(",\"initial_value\" : \"%s\"", extraParams.toString())
							: "");
			StrSubstitutor sub = new StrSubstitutor(values, "${", "}");
			String bodyWInitValues = sub.replace(viewJson);
			ViewsOpenRequest viewsOpenRequest = ViewsOpenRequest.builder().triggerId(triggerId)
					.viewAsString(bodyWInitValues).build();
			ViewsOpenResponse openResponse = ctx.client().viewsOpen(viewsOpenRequest);
			if (openResponse.getError() != null) {
				log.error("Error during view opening: " + openResponse.getError());
			}

			return rs;
		})

				.save((req, ctx) -> {
					log.info("Save workflow step by user " + req.getPayload().getUser().getUsername());
					Map<String, Map<String, ViewState.Value>> stateValues = req.getPayload().getView().getState()
							.getValues();
					Map<String, WorkflowStepInput> inputs = new HashMap<>();
					stateValues.keySet().stream().forEach(k -> {
						inputs.put(k, stepInput(i -> i.value(extract(stateValues, k, k))));
					});
					List<WorkflowStepOutput> outputs = asStepOutputs(
					// stepOutput(o ->
					// o.name(Keys.routing_key.toString()).type("text").label("RabbitMQ routing
					// key"))
					// stepOutput(o -> o.name(Keys.repo.toString()).type("text").label("Name of test
					// project repository")),
					// stepOutput(o -> o.name(Keys.suite.toString()).type("text").label("Test suite
					// to run"))
					);
					ctx.update(inputs, outputs);
					return ctx.ack();
				})

				.execute((req, ctx) -> {
					log.info("Execute workflow step");
					WorkflowStepExecution wfStep = req.getPayload().getEvent().getWorkflowStep();
					Map<String, Object> outputs = new HashMap<>();
					wfStep.getInputs().keySet().stream().forEach(k -> {
						outputs.put(k.toString(), wfStep.getInputs().get(k).getValue());
					});
					try {
						log.debug("MAP: " + StringUtils.join(outputs));
						Map<String, String> values = new HashMap<String, String>();
						values.put("routingKey", outputs.get(Keys.routing_key.toString()).toString());
						values.put("jobName", outputs.get(Keys.suite.toString()).toString());
						values.put("projectName", outputs.get(Keys.repo.toString()).toString());
						final StringBuilder jobParams = new StringBuilder();
						outputs.keySet().stream().filter(k -> !EnumUtils.isValidEnum(Keys.class, k))
								.forEach(k -> jobParams.append(String.format("&%s=%s", k, outputs.get(k))));
						if (jobParams.length() > 0) {
							jobParams.deleteCharAt(0);
						}
						values.put("jobParams", jobParams.toString());
						StrSubstitutor sub = new StrSubstitutor(values, "${", "}");
						String body = sub.replace(FileUtil.getResourceFileAsString("rabbitmq/publish_msg.json"));
						RestAssured.given().body(body).contentType(JSON).accept(JSON).urlEncodingEnabled(false).auth()
								.basic(System.getenv("RABBIT_LOGIN"), System.getenv("RABBIT_PSWD"))
								.post(System.getenv("RABBIT_PUBLISH_URL")).then().and().assertThat().statusCode(200)
								.body("routed", Matchers.equalTo(true));

						ctx.complete(outputs);

						log.info("Job was successfully triggered");
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
