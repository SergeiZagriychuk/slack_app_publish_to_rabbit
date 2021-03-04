package com.solvd.qa.workflow.step.builder;

import static com.slack.api.model.workflow.WorkflowSteps.asStepOutputs;
import static com.slack.api.model.workflow.WorkflowSteps.stepInput;
import static com.solvd.qa.util.BuilderUtil.extract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.EnumUtils;

import com.slack.api.bolt.handler.builtin.WorkflowStepExecuteHandler;
import com.slack.api.bolt.middleware.builtin.WorkflowStep;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.model.view.ViewState;
import com.slack.api.model.workflow.WorkflowStepInput;
import com.slack.api.model.workflow.WorkflowStepOutput;
import com.solvd.qa.util.FreemarkerUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractWorkflow {
	
	protected abstract WorkflowStepExecuteHandler buildExecuteStep();
	
	protected abstract boolean isRabbitFlow();
	
	protected abstract String getCallbackName();
	
	public WorkflowStep buildStep() {

		WorkflowStep step = WorkflowStep.builder().callbackId(getCallbackName()).edit((req, ctx) -> {
			log.info("Edit workflow step '" + getCallbackName() + "' by user " + req.getPayload().getUser().getUsername());
			String triggerId = req.getPayload().getTriggerId();
			com.slack.api.bolt.response.Response rs = ctx.ack();

			Properties p = new Properties();
			Map<String, WorkflowStepInput> inputs = req.getPayload().getWorkflowStep().getInputs();
			if(isRabbitFlow()) {
				p.put("rabbit_flow", true);
				if(inputs.containsKey(Keys.routing_key.toString())) {
					p.put("routing_key_initial_value", inputs.get(Keys.routing_key.toString()).getValue().toString());
				}
			}
			if(inputs.containsKey(Keys.repo.toString())) {
				p.put("repo_initial_value", inputs.get(Keys.repo.toString()).getValue().toString());
			}
			if(inputs.containsKey(Keys.suite.toString())) {
				p.put("suite_initial_value", inputs.get(Keys.suite.toString()).getValue().toString());
			}
			StringBuilder extraParams = new StringBuilder();
			inputs.keySet().stream().filter(k -> !EnumUtils.isValidEnum(Keys.class, k))
					.forEach(k -> extraParams.append(k + " "));
			if(extraParams.length() != 0) {
				p.put("params_initial_value", extraParams.toString());
			}
			String bodyWInitValues = FreemarkerUtil.processTemplate("views/init_job_view.json", p);
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

		.execute(buildExecuteStep()).build();

		return step;
	}

}
