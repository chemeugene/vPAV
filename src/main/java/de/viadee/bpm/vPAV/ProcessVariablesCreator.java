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
package de.viadee.bpm.vPAV;

import de.viadee.bpm.vPAV.constants.BpmnConstants;
import de.viadee.bpm.vPAV.processing.ObjectReader;
import de.viadee.bpm.vPAV.processing.code.flow.BasicNode;
import de.viadee.bpm.vPAV.processing.code.flow.BpmnElement;
import de.viadee.bpm.vPAV.processing.code.flow.Node;
import de.viadee.bpm.vPAV.processing.model.data.*;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.BpmnModelElementInstance;
import soot.Value;
import soot.toolkits.graph.Block;

import java.util.ArrayList;
import java.util.List;

public class ProcessVariablesCreator {

    private static final int CONTROL_NONE = 0;

    private static final int CONTROL_IF = 1;

    private static final int CONTROL_ELSE = 2;

    private static final int CONTROL_COND_END = 3;

    private ArrayList<Node> nodes = new ArrayList<>();

    private BpmnElement element;

    private ElementChapter chapter;

    private KnownElementFieldType fieldType;

    private String defaultScopeId;

    // Stack of successor nodes
    private ArrayList<Node> stack = new ArrayList<>();

    // Used for testing
    ProcessVariablesCreator(final BpmnElement element,
            final ElementChapter chapter, final KnownElementFieldType fieldType, String scopeId) {
        //    variableBlock = new VariableBlock(block, new ArrayList<>());
        this.element = element;
        this.chapter = chapter;
        this.fieldType = fieldType;
        defaultScopeId = scopeId;
    }

    // Does it make sense to store the top-level block or are there better ways?
    public ProcessVariablesCreator(final BpmnElement element,
            final ElementChapter chapter, final KnownElementFieldType fieldType) {
        //    variableBlock = new VariableBlock(block, new ArrayList<>());
        this.element = element;
        this.chapter = chapter;
        this.fieldType = fieldType;
        determineScopeId();
    }

    // Only called for top-level block -> Rename
    // TODO do not return variable block, not needed at the moment
    public void blockIterator(final Block block, final List<Value> args) {
        ObjectReader objectReader = new ObjectReader(this);
        objectReader.processBlock(block, args, null);
        cleanEmptyNodes();
    }

    // This method adds process variables one by one
    public void handleProcessVariableManipulation(Block block, ProcessVariableOperation pvo) {
        pvo.setIndex(element.getFlowAnalysis().getOperationCounter());
        element.getFlowAnalysis().incrementOperationCounter();

        // Block hasn't changed since the last operation, add operation to existing block
        if (nodes.size() > 0 && lastNode().getBlock().equals(block)) {
            lastNode().addOperation(pvo);
        } else {
            // Add new block
            Node node = new Node(element, block, chapter, fieldType);
            node.addOperation(pvo);
            nodes.add(node);
            element.getControlFlowGraph().addNode(node);
            if (!stack.isEmpty()) {
                node.addPredecessor(peekStack());
            }
        }
    }

    private void determineScopeId() {
        // TODO there might be another "calculation" for multi instance tasks
        final BaseElement baseElement = element.getBaseElement();
        BpmnModelElementInstance scopeElement = baseElement.getScope();

        String scopeId = null;
        if (scopeElement != null) {
            scopeId = scopeElement.getAttributeValue(BpmnConstants.ATTR_ID);
        }
        this.defaultScopeId = scopeId;
    }

    public String getScopeId() {
        return defaultScopeId;
    }

    private Node lastNode() {
        return nodes.get(nodes.size() - 1);
    }

    private Node peekStack() {
        return stack.get(stack.size() - 1);
    }

    private void popStack() {
        stack.remove(stack.size() - 1);
    }

    private void cleanEmptyNodes() {
        // Clean up nodes without operations to make analysis faster
        for(Node node: nodes) {
            if(node.getOperations().size() == 0) {
                element.getControlFlowGraph().removeNode(node);
                // For all predecessors, remove node from successors and add successors of node
                node.getPredecessors().forEach(pred -> {
                    pred.removeSuccessor(node.getId());
                    node.getSuccessors().forEach(pred::addSuccessor);
                });
                // For all successors, remove node from predecessors and add predecessors of node
                node.getSuccessors().forEach(succ -> {
                    succ.removePredecessor(node.getId());
                    node.getPredecessors().forEach(succ::addPredecessor);
                });
            }
        }
    }

    public void startSuccessorHandling(Block block) {
        // Check if block is already associated with one node, if not create an empty one
        boolean exists = false;
        // Start from back as this should be faster
        for (int i = nodes.size() - 1; i >= 0; i--) {
            if (nodes.get(i).getBlock().equals(block)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            Node node = new Node(element, block, chapter, fieldType);
            nodes.add(node);
            element.getControlFlowGraph().addNode(node);
            if(!stack.isEmpty()) {
                node.addPredecessor(peekStack());
            }
        }
        stack.add(lastNode());
    }

    public void endSuccessorHandling() {
        if (!stack.isEmpty()) {
            popStack();
        }
    }

    public void visitBlockAgain(Block block) {
        // find first node that is associated with block and set successor
        for (Node node : nodes) {
            if (ObjectReader.hashBlock(node.getBlock()) == ObjectReader.hashBlock(block)) {
                node.addPredecessor(peekStack());
                break;
            }
        }
    }

    // TODO recursion based on blocks (maybe hashing if already inside?
}