package com.solvd.qa.workflow.step.builder;

import static io.restassured.http.ContentType.JSON;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;

import com.slack.api.bolt.context.builtin.WorkflowStepExecuteContext;
import com.slack.api.bolt.handler.builtin.WorkflowStepExecuteHandler;
import com.slack.api.bolt.request.builtin.WorkflowStepExecuteRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.workflow.WorkflowStepExecution;
import com.solvd.qa.enums.Keys;
import com.solvd.qa.util.FreemarkerUtil;

import io.restassured.RestAssured;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RabbitWorkflow extends AbstractWorkflow {

	public final static String CALLBACK_ID = "start_tests_rabbit";

	@Override
	protected boolean isRabbitFlow() {
		return true;
	}

	@Override
	protected String getCallbackName() {
		return CALLBACK_ID;
	}

	@Override
	protected WorkflowStepExecuteHandler buildExecuteStep() {
		return new WorkflowStepExecuteHandler() {

			@SuppressWarnings("unchecked")
			@Override
			public Response apply(WorkflowStepExecuteRequest req, WorkflowStepExecuteContext ctx)
					throws IOException, SlackApiException {
				log.info("Execute workflow step for RabbitMQ flow");
				WorkflowStepExecution wfStep = req.getPayload().getEvent().getWorkflowStep();
				Map<String, Object> outputs = new HashMap<>();
				wfStep.getInputs().keySet().stream().forEach(k -> {
					outputs.put(k.toString(), wfStep.getInputs().get(k).getValue());
				});
				try {
					log.debug("MAP: " + StringUtils.join(outputs));
					Properties p = new Properties();
					p.put("routingKey", outputs.get(Keys.routing_key.toString()).toString());
					p.put("jobName", outputs.get(Keys.suite.toString()).toString());
					p.put("projectName", outputs.get(Keys.repo.toString()).toString());
					final StringBuilder jobParams = new StringBuilder();
					outputs.keySet().stream().filter(k -> !EnumUtils.isValidEnum(Keys.class, k))
							.forEach(k -> jobParams.append(String.format("&%s=%s", k, outputs.get(k))));
					if (jobParams.length() > 0) {
						jobParams.deleteCharAt(0);
					}
					p.put("jobParams", jobParams.toString());
					String body = FreemarkerUtil.processTemplate("rabbitmq/publish_msg.json", p);

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
			}
		};
	}

}
