/**
 * BSD 3-Clause License
 *
 * Copyright © 2019, viadee Unternehmensberatung AG
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.viadee.bpm.vPAV.processing;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import de.viadee.bpm.vPAV.BpmnScanner;
import de.viadee.bpm.vPAV.FileScanner;
import de.viadee.bpm.vPAV.config.model.Rule;
import de.viadee.bpm.vPAV.constants.BpmnConstants;
import de.viadee.bpm.vPAV.processing.checker.CheckerFactory;
import de.viadee.bpm.vPAV.processing.checker.DataFlowChecker;
import de.viadee.bpm.vPAV.processing.checker.ElementChecker;
import de.viadee.bpm.vPAV.processing.checker.ProcessVariablesModelChecker;
import de.viadee.bpm.vPAV.processing.code.flow.BpmnElement;
import de.viadee.bpm.vPAV.processing.code.flow.ControlFlowGraph;
import de.viadee.bpm.vPAV.processing.code.flow.FlowAnalysis;
import de.viadee.bpm.vPAV.processing.dataflow.DataFlowRule;
import de.viadee.bpm.vPAV.processing.model.data.CheckerIssue;
import de.viadee.bpm.vPAV.processing.model.data.ModelDispatchResult;
import de.viadee.bpm.vPAV.processing.model.data.ProcessVariable;
import de.viadee.bpm.vPAV.processing.model.data.ProcessVariableOperation;
import de.viadee.bpm.vPAV.processing.model.graph.Graph;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;

import java.io.File;
import java.util.*;

/**
 * Calls model and element checkers for a concrete bpmn processdefinition
 *
 */
public class BpmnModelDispatcher {

	private Map<String, String> incorrectCheckers = new HashMap<>();

	/**
	 * The BpmnModelDispatcher reads a model and creates a collection of all
	 * elements. Iterates through collection and checks each element for validity
	 * Additionally a graph is created to check for invalid paths.
	 * 
	 * @param fileScanner
	 *            - FileScanner
	 * @param processDefinition
	 *            - Holds the path to the BPMN model
	 * @param scanner
	 *            - OuterProcessVariableScanner
	 * @param dataFlowRules
	 *            - DataFlowRules to be checked for
	 * @param conf
	 *            - ruleSet
	 * @return issues
	 */
	public ModelDispatchResult dispatchWithVariables(final FileScanner fileScanner, final File processDefinition,
													 final ProcessVariablesScanner scanner, final Collection<DataFlowRule> dataFlowRules,
													 final Map<String, Map<String, Rule>> conf) {

		final Collection<CheckerIssue> issues = new ArrayList<>();
		final BpmnScanner bpmnScanner = createScanner(processDefinition);

		// parse bpmn model
		final BpmnModelInstance modelInstance = Bpmn.readModelFromFile(processDefinition);

		// hold bpmn elements
		final Collection<BaseElement> baseElements = modelInstance.getModelElementsByType(BaseElement.class);

		// ProcessVariables model checker
		final Rule rule = conf.get(BpmnConstants.PROCESS_VARIABLE_MODEL_CHECKER)
				.get(BpmnConstants.PROCESS_VARIABLE_MODEL_CHECKER);
		ProcessVariableReader variableReader = new ProcessVariableReader(rule, bpmnScanner, fileScanner);
		final ElementGraphBuilder graphBuilder = new ElementGraphBuilder(
				scanner.getMessageIdToVariableMap(), scanner.getProcessIdToVariableMap(), bpmnScanner, variableReader, fileScanner);

		// create data flow graphs for bpmn model
		final Collection<Graph> graphCollection = graphBuilder.createProcessGraph(modelInstance,
				processDefinition.getPath(), new ArrayList<>(), scanner);

		// analyze data flows
		final FlowAnalysis flowAnalysis = new FlowAnalysis();
		flowAnalysis.analyze(graphCollection);


		// call model checkers
		// TODO: move it to a factory class later
		if (rule != null && rule.isActive()) {
			final ProcessVariablesModelChecker processVarChecker = new ProcessVariablesModelChecker(rule, bpmnScanner, processDefinition, fileScanner, scanner);
			issues.addAll(processVarChecker.check());
		}


		// Data flow checker
		final Collection<BpmnElement> bpmnElements = getBpmnElements(processDefinition, baseElements, graphBuilder);
		final Collection<ProcessVariable> processVariables = getProcessVariables(bpmnElements);


		final Rule dataFlowRule = conf.get(getClassName(DataFlowChecker.class))
				.get(getClassName(DataFlowChecker.class));
		if (dataFlowRule != null && dataFlowRule.isActive() && !dataFlowRules.isEmpty()) {
			final DataFlowChecker dataFlowChecker = new DataFlowChecker(rule, bpmnScanner, processDefinition, fileScanner, scanner);
			issues.addAll(dataFlowChecker.check());
		}

		// Element checkers
		// create checkerInstances
		Collection<ElementChecker> checkerInstances = createCheckerInstances(fileScanner.getResourcesNewestVersions(), conf, bpmnScanner,
				scanner);

		executeCheckers(processDefinition, baseElements, graphBuilder, issues, checkerInstances);

		return new ModelDispatchResult(issues, bpmnElements, processVariables);
	}

	/**
	 * The BpmnModelDispatcher reads a model and creates a collection of all
	 * elements. Iterates through collection and checks each element for validity
	 * Additionally a graph is created to check for invalid paths.
	 *
	 * @param processDefinition
	 *            Holds the path to the BPMN model
	 * @param decisionRefToPathMap
	 *            decisionRefToPathMap
	 * @param processIdToPathMap
	 *            Map of prozessId to bpmn file
	 * @param resourcesNewestVersions
	 *            collection with newest versions of class files
	 * @param conf
	 *            ruleSet
	 * @return issues
	 */
	public ModelDispatchResult dispatchWithoutVariables(final File processDefinition,
			final Map<String, String> decisionRefToPathMap, final Map<String, String> processIdToPathMap,
			final Collection<String> resourcesNewestVersions, final Map<String, Map<String, Rule>> conf) {

		BpmnScanner bpmnScanner = createScanner(processDefinition);

		// parse bpmn model
		final BpmnModelInstance modelInstance = Bpmn.readModelFromFile(processDefinition);

		// hold bpmn elements
		final Collection<BaseElement> baseElements = modelInstance.getModelElementsByType(BaseElement.class);

		final ElementGraphBuilder graphBuilder = new ElementGraphBuilder(decisionRefToPathMap, processIdToPathMap,
				bpmnScanner);

		final Collection<CheckerIssue> issues = new ArrayList<>();

		// create checkerInstances as singletons
		Collection<ElementChecker> checkerInstances = createCheckerInstances(resourcesNewestVersions, conf, bpmnScanner,
				null);

		executeCheckers(processDefinition, baseElements, graphBuilder, issues, checkerInstances);

		return new ModelDispatchResult(issues, getBpmnElements(processDefinition, baseElements, graphBuilder),
				Collections.emptyList());
	}

	/**
	 * @param baseElements
	 *            Collection of baseElements
	 * @param graphBuilder
	 *            graphBuilder
	 * @param processDefinition
	 *            bpmn file
	 * @return Collection of BpmnElements
	 */
	public static Collection<BpmnElement> getBpmnElements(final File processDefinition,
			final Collection<BaseElement> baseElements, final ElementGraphBuilder graphBuilder) {
		final List<BpmnElement> elements = new ArrayList<>();
		for (final BaseElement baseElement : baseElements) {
			BpmnElement element = graphBuilder.getElement(baseElement.getId());
			if (element == null) {
				// if element is not in the data flow graph, create it.
				ControlFlowGraph controlFlowGraph = new ControlFlowGraph();
				element = new BpmnElement(processDefinition.getPath(), baseElement, controlFlowGraph);
			}
			elements.add(element);
		}
		return elements;
	}

	/**
	 * @param elements
	 *            Collection of BPMN elements
	 * @return Collection of process variables
	 */
	public static Collection<ProcessVariable> getProcessVariables(final Collection<BpmnElement> elements) {
		// write variables containing elements
		// first, we need to inverse mapping to process variable -> operations
		// (including element)
		final ListMultimap<String, ProcessVariable> variables = ArrayListMultimap.create();
		for (final BpmnElement element : elements) {
			for (final ProcessVariableOperation variableOperation : element.getProcessVariables().values()) {
				final String variableName = variableOperation.getName();
				if (!variables.containsKey(variableName)) {
					variables.put(variableName, new ProcessVariable(variableName));
				}
				final Collection<ProcessVariable> processVariables = variables.asMap().get(variableName);
				for (ProcessVariable pv : processVariables) {
					switch (variableOperation.getOperation()) {
					case READ:
						pv.addRead(variableOperation);
						break;
					case WRITE:
						pv.addWrite(variableOperation);
						break;
					case DELETE:
						pv.addDelete(variableOperation);
						break;
					}
				}
			}
		}
		return variables.values();
	}

	/**
	 * 
	 * @param processDefinition
	 *            Holds the path to the BPMN model
	 * @param baseElements
	 *            List of baseElements
	 * @param graphBuilder
	 *            ElementGraphBuilder used for data flow of a BPMN Model
	 * @param issues
	 *            List of issues
	 * @param checkerInstances
	 *            ElementCheckers from ruleSet
	 */
	private void executeCheckers(final File processDefinition, final Collection<BaseElement> baseElements,
			final ElementGraphBuilder graphBuilder, final Collection<CheckerIssue> issues,
			Collection<ElementChecker> checkerInstances) {
		// execute element checkers
		for (final BaseElement baseElement : baseElements) {
			BpmnElement element = graphBuilder.getElement(baseElement.getId());
			if (element == null) {
				// if element is not in the data flow graph, create it.
				ControlFlowGraph controlFlowGraph = new ControlFlowGraph();
				element = new BpmnElement(processDefinition.getPath(), baseElement, controlFlowGraph);
			}
			for (final ElementChecker checker : checkerInstances) {
				issues.addAll(checker.check(element));
			}
		}
	}

	/**
	 * 
	 * @param processDefinition
	 *            Holds the path to the BPMN model
	 * @return BpmnScanner
	 */
	public BpmnScanner createScanner(final File processDefinition) {
		// create BPMNScanner
		return new BpmnScanner(processDefinition.getPath());
	}

	/**
	 * 
	 * @param resourcesNewestVersions
	 *            Resources with their newest version as found on classpath during
	 *            runtime
	 * @param conf
	 *            ruleSet
	 * @param bpmnScanner
	 *            BPMNScanner
	 * @param scanner
	 *            ProcessVariablesScanner
	 * @return CheckerCollection
	 */
	protected Collection<ElementChecker> createCheckerInstances(final Collection<String> resourcesNewestVersions,
			final Map<String, Map<String, Rule>> conf, final BpmnScanner bpmnScanner,
			final ProcessVariablesScanner scanner) {
		CheckerFactory checkerFactory = new CheckerFactory();

		final Collection<ElementChecker> checkerCollection = checkerFactory.createCheckerInstances(conf,
				resourcesNewestVersions, bpmnScanner, scanner);

		setIncorrectCheckers(checkerFactory.getIncorrectCheckers());

		return checkerCollection;
	}

	private String getClassName(Class<?> clazz) {
		return clazz.getSimpleName();
	}

	public Map<String, String> getIncorrectCheckers() {
		return incorrectCheckers;
	}

	public void setIncorrectCheckers(Map<String, String> incorrectCheckers) {
		this.incorrectCheckers = incorrectCheckers;
	}
}
