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
package de.viadee.bpm.vPAV.processing.code.flow;

import de.viadee.bpm.vPAV.processing.model.data.ProcessVariableOperation;

import java.util.Iterator;
import java.util.LinkedHashMap;

public class ControlFlowGraph {

	private LinkedHashMap<String, Node> nodes;

	private LinkedHashMap<String, ProcessVariableOperation> operations;

	private int internalNodeCounter;

	private int recursionCounter;

	private int nodeCounter;

	private int priorLevel;

	public ControlFlowGraph() {
		nodes = new LinkedHashMap<>();
		this.operations = new LinkedHashMap<>();
		nodeCounter = -1;
		internalNodeCounter = 0;
		recursionCounter = 0;
		priorLevel = 0;
	}

	/**
	 * Adds a node to the current CFG
	 *
	 * @param node
	 *            Node to be added to the control flow graph
	 */
	public void addNode(final Node node) {
		String key = createHierarchy(node);
		node.setId(key);
		this.nodes.put(key, node);
	}

	/**
	 *
	 * Helper method to create ids based on hierarchy, e.g.
	 *
	 * 0 --> 1 --> 2 --> 3 --> 3.0 --> 3.0.0 --> 3.0.1 --> 3.0.2
	 * 
	 * @param node
	 *            Current node
	 * @return Id of node
	 */
	private String createHierarchy(final Node node) {
		StringBuilder key = new StringBuilder();
		key.append(node.getParentElement().getBaseElement().getId()).append("__");
		if (recursionCounter == 0) {
			nodeCounter++;
			key.append(nodeCounter);
		} else {
			key.append(nodeCounter);
			for (int i = 1; i <= recursionCounter; i++) {
				key.append(".");
				if (i == recursionCounter) {
					key.append(internalNodeCounter);
					String predKey = key.toString();
					if (internalNodeCounter == 0) {
						node.setPredsInterProcedural(predKey.substring(0, predKey.length() - 2));
					}
				} else {
					key.append(priorLevel);
				}
			}
			internalNodeCounter++;
		}
		return key.toString();
	}

	/**
	 * Set predecessor/successor relations for blocks and initialize sets
	 */
	void computePredecessorRelations() {
		nodes.values().forEach(node -> {
			this.operations.putAll(node.getOperations());
			node.setPreds();
			node.setSuccs();
			node.setOutUnused(new LinkedHashMap<>());
			node.setOutUsed(new LinkedHashMap<>());
		});
	}

	boolean hasNodes() {
		return !nodes.isEmpty();
	}

	public void incrementRecursionCounter() {
		this.recursionCounter++;
	}

	public void decrementRecursionCounter() {
		this.recursionCounter--;
	}

	public void resetInternalNodeCounter() {
		this.internalNodeCounter = 0;
	}

	public int getInternalNodeCounter() {
		return internalNodeCounter;
	}

	public void setPriorLevel(int priorLevel) {
		this.priorLevel = priorLevel;
	}

	public LinkedHashMap<String, Node> getNodes() {
		return nodes;
	}

	public int getPriorLevel() {
		return priorLevel;
	}

	public void setInternalNodeCounter(int internalNodeCounter) {
		this.internalNodeCounter = internalNodeCounter;
	}

	Node firstNode() {
		Iterator<Node> iterator = nodes.values().iterator();
		return iterator.next();
	}

	Node lastNode() {
		Iterator<Node> iterator = nodes.values().iterator();
		Node node = null;
		while (iterator.hasNext()) {
			node = iterator.next();
		}
		return node;
	}
}
