package com.solvd.qa.workflow.step.builder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.EnumUtils;

import com.slack.api.bolt.context.builtin.WorkflowStepExecuteContext;
import com.slack.api.bolt.handler.builtin.WorkflowStepExecuteHandler;
import com.slack.api.bolt.request.builtin.WorkflowStepExecuteRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.workflow.WorkflowStepExecution;
import com.solvd.qa.enums.Keys;
import com.solvd.qa.model.JenkinsAuth;
import com.solvd.qa.util.workflow.AmazonS3JenkinsAuthService;

import io.restassured.RestAssured;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JenkinsWorkflow extends AbstractWorkflow {

	public final static String CALLBACK_ID = "start_tests_jenkins";

	@Override
	protected boolean isRabbitFlow() {
		return false;
	}

	@Override
	protected String getCallbackName() {
		return CALLBACK_ID;
	}

	@Override
	protected WorkflowStepExecuteHandler buildExecuteStep() {
		return new WorkflowStepExecuteHandler() {

			@Override
			public Response apply(WorkflowStepExecuteRequest req, WorkflowStepExecuteContext ctx)
					throws IOException, SlackApiException {
				log.info("Execute workflow step for Jenkins flow");
				WorkflowStepExecution wfStep = req.getPayload().getEvent().getWorkflowStep();
				Map<String, Object> outputs = new HashMap<>();
				wfStep.getInputs().keySet().stream().forEach(k -> {
					outputs.put(k.toString(), wfStep.getInputs().get(k).getValue());
				});
				try {
					Object tenant = outputs.get(Keys.tenant.toString());
					Object repo = outputs.get(Keys.repo.toString());
					Object suite = outputs.get(Keys.suite.toString());
					String url;
					if (tenant != null) {
						url = String.format("%sjob/%s/job/%s/job/%s/buildWithParameters", System.getenv("JENKINS_URL"),
								tenant, repo, suite);
					} else if (outputs.get(Keys.repo.toString()) != null) {
						url = String.format("%s/job/%s/job/%s/buildWithParameters", System.getenv("JENKINS_URL"), repo,
								suite);
					} else {
						url = String.format("%s/job/%s/buildWithParameters", System.getenv("JENKINS_URL"), suite);
					}

					Map<String, Object> jobParams = outputs.entrySet().stream()
							.filter(e -> !EnumUtils.isValidEnum(Keys.class, e.getKey()))
							.collect(Collectors.toMap(x -> x.getKey(), x -> x.getValue()));

					JenkinsAuth auth = new AmazonS3JenkinsAuthService().getAuthForTeam(req.getPayload().getTeamId());

					RestAssured.given().urlEncodingEnabled(false).auth().preemptive()
							.basic(auth.getUsername(), auth.getApiToken()).params(jobParams).post(url).then().and()
							.assertThat().statusCode(201);

					ctx.complete(outputs);

					log.info("Jenkins job was successfully triggered");
				} catch (Exception e) {
					Map<String, Object> error = new HashMap<>();
					error.put("message", "Something wrong!" + System.lineSeparator() + e.getMessage());
					ctx.fail(error);
				}
				return ctx.ack();
			}
		};
	}

}
