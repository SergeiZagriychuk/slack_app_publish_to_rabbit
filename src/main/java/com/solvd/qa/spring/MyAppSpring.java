package com.solvd.qa.spring;

import static com.slack.api.model.block.Blocks.asBlocks;
import static com.slack.api.model.block.Blocks.divider;
import static com.slack.api.model.block.Blocks.input;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.plainTextInput;
import static com.slack.api.model.view.Views.view;
import static com.slack.api.model.workflow.WorkflowSteps.asStepOutputs;
import static com.slack.api.model.workflow.WorkflowSteps.stepInput;
import static io.restassured.http.ContentType.JSON;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.hamcrest.Matchers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.slack.api.bolt.App;
import com.slack.api.bolt.middleware.builtin.WorkflowStep;
import com.slack.api.bolt.service.OAuthStateService;
import com.slack.api.bolt.service.builtin.AmazonS3InstallationService;
import com.slack.api.bolt.service.builtin.AmazonS3OAuthStateService;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.request.views.ViewsUpdateRequest;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.methods.response.views.ViewsUpdateResponse;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.event.AppHomeOpenedEvent;
import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewState;
import com.slack.api.model.workflow.WorkflowStepExecution;
import com.slack.api.model.workflow.WorkflowStepInput;
import com.slack.api.model.workflow.WorkflowStepOutput;
import com.solvd.qa.util.FileUtil;

import io.restassured.RestAssured;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class MyAppSpring {

	private enum Keys {
		routing_key, suite, repo
	}

	private final static String BUCKET_NAME = System.getenv("S3_BUCKET_NAME");

	@Bean
	public App initSlackApp() {
		var app = new App().asOAuthApp(true);

		AmazonS3InstallationService installationService = new AmazonS3InstallationService(BUCKET_NAME);
		installationService.setHistoricalDataEnabled(true);
		OAuthStateService stateService = new AmazonS3OAuthStateService(BUCKET_NAME);

		app.service(installationService).service(stateService);

		app.event(AppHomeOpenedEvent.class, (payload, ctx) -> {
			var appHomeView = view(view -> view.type("home").blocks(asBlocks(
					section(section -> section.text(markdownText(mt -> mt.text(
							"*This is slack app which helps to publish RabbitMQ requests for Jenkins job triggering.*")))),
					divider(), section(section -> section.text(markdownText(mt -> mt
							.text("Such triggering can be added as a step for your custom Workflow in slack")))))));

			ctx.client().viewsPublish(r -> r.userId(payload.getEvent().getUser()).view(appHomeView));

			return ctx.ack();
		});

		/**
		 * Workflow step setting up
		 */
		@SuppressWarnings("unchecked")
		WorkflowStep step = WorkflowStep.builder().callbackId("start_tests")

				.edit((req, ctx) -> {
					log.info("Edit workflow step by user " + req.getPayload().getUser().getUsername());
					String triggerId = req.getPayload().getTriggerId();
					com.slack.api.bolt.response.Response rs = ctx.ack();

					String viewJson = FileUtil.getResourceFileAsString("views/init_job_view.json");
					Map<String, String> values = new HashMap<String, String>();
					Map<String, WorkflowStepInput> inputs = req.getPayload().getWorkflowStep().getInputs();
					values.put("routing_key_initial_value",
							inputs.containsKey(Keys.routing_key.toString())
									? String.format(",\"initial_value\" : \"%s\"",
											inputs.get(Keys.routing_key.toString()).getValue().toString())
									: "");
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
							extraParams.length() != 0
									? String.format(",\"initial_value\" : \"%s\"", extraParams.toString())
									: "");
					StrSubstitutor sub = new StrSubstitutor(values, "${", "}");
					String bodyWInitValues = sub.replace(viewJson);
					System.err.println(bodyWInitValues);
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

		app.step(step);

		/**
		 * handling of custom parameters setting
		 */
		app.blockAction("job_params_entered", (req, ctx) -> {
			log.info("Submit job params by user " + req.getPayload().getUser().getUsername());
			com.slack.api.bolt.response.Response rs = ctx.ack();

			Map<String, Map<String, ViewState.Value>> stateValues = req.getPayload().getView().getState().getValues();
			String[] params = ((String) stepInput(
					i -> i.value(extract(stateValues, "custom_params", "job_params_entered"))).getValue()).trim()
							.split("\\s+");
			log.info("Custom job parameters: " + Arrays.toString(params));

			List<LayoutBlock> layouts = new ArrayList<LayoutBlock>();
			req.getPayload().getView().getBlocks().remove(req.getPayload().getView().getBlocks().size() - 1);
			layouts.addAll(req.getPayload().getView().getBlocks());
			Arrays.asList(params).stream().forEach(p -> {
				layouts.add(input(input -> input.blockId(p).label(plainText("Select value for parameter " + p))
						.element(plainTextInput(plainTxt -> plainTxt.actionId(p)))));
			});

			View viewUpd = view(view -> view.type("workflow_step").callbackId("start_tests")
					.blocks(asBlocks(layouts.toArray(new LayoutBlock[layouts.size()]))));
			ViewsUpdateRequest viewsUpdateRequest = ViewsUpdateRequest.builder()
					.viewId(req.getPayload().getView().getId()).view(viewUpd).build();
			ViewsUpdateResponse updateResponse = ctx.client().viewsUpdate(viewsUpdateRequest);
			if (updateResponse.getError() != null) {
				log.error("Error during view updating: " + updateResponse.getError());
			}

			return rs;
		});

		app.endpoint("GET", "/slack/oauth/completion", (req, ctx) -> {
			return com.slack.api.bolt.response.Response.builder().statusCode(200).contentType("text/html")
					.body(SlackAuthSuccessController.renderCompletionPageHtml(req.getQueryString())).build();
		});

		app.endpoint("GET", "/slack/oauth/cancellation", (req, ctx) -> {
			return com.slack.api.bolt.response.Response.builder().statusCode(200).contentType("text/html")
					.body(SlackAuthCancelController.renderCancellationPageHtml(req.getQueryString())).build();
		});

		return app;
	}

	private static String extract(Map<String, Map<String, ViewState.Value>> stateValues, String blockId,
			String actionId) {
		return stateValues.get(blockId).get(actionId).getValue();
	}

}
