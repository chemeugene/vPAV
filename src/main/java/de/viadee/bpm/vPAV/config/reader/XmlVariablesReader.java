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
package de.viadee.bpm.vPAV.config.reader;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import de.viadee.bpm.vPAV.RuntimeConfig;
import de.viadee.bpm.vPAV.processing.model.data.ElementChapter;
import de.viadee.bpm.vPAV.processing.model.data.KnownElementFieldType;
import de.viadee.bpm.vPAV.processing.model.data.ProcessVariableOperation;
import de.viadee.bpm.vPAV.processing.model.data.VariableOperation;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Used to read the variables file (variables.xml) and extract the user defined variables
 * Requirements: Existing variables.xml in src/test/resources
 */
public final class XmlVariablesReader {

    private static final Logger LOGGER = Logger.getLogger(XmlVariablesReader.class.getName());

    /**
     * @param file Location of file relative to project
     * @return
     * @throws ConfigReaderException If file can not be found in classpath
     */
    public HashMap<String, ListMultimap<String, ProcessVariableOperation>> read(final String file)
            throws ConfigReaderException {

        try {
            final JAXBContext jaxbContext = JAXBContext.newInstance(XmlVariables.class);
            final Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();

            InputStream fVariables = RuntimeConfig.getInstance().getClassLoader().getResourceAsStream(file);

            if (fVariables != null) {
                final XmlVariables xmlVariables = (XmlVariables) jaxbUnmarshaller.unmarshal(fVariables);
                return transformFromXmlDestructure(xmlVariables);
            } else {
                LOGGER.log(Level.INFO, "No variables.xml file with user defined variables was found.");
                return new HashMap<>();
            }
        } catch (JAXBException e) {
            throw new ConfigReaderException(e);
        }
    }

    /**
     * Transforms XmlRuleSet to rules
     *
     * @param ruleSet RuleSet as XmlRuleSet
     * @return rules Transformed map of rules
     * @throws ConfigReaderException If file could not be read properly
     */
    /**
     * @param xmlVariables
     * @return
     * @throws ConfigReaderException
     */
    private static HashMap<String, ListMultimap<String, ProcessVariableOperation>> transformFromXmlDestructure(
            final XmlVariables xmlVariables)
            throws ConfigReaderException {
        final Collection<XmlVariable> variableCollection = xmlVariables.getVariables();
        HashMap<String, ListMultimap<String, ProcessVariableOperation>> operations = new HashMap<>();

        for (final XmlVariable variable : variableCollection) {
            ProcessVariableOperation operation = null;
            try {
                operation = createOperationFromXml(variable);
            } catch (VariablesReaderException e) {
                e.printStackTrace();
            }
            if(!operations.containsKey(variable.getCreationPoint())) {
                operations.put(variable.getCreationPoint(), ArrayListMultimap.create());
            }
            operations.get(variable.getCreationPoint()).put(operation.getName(), operation);
        }

        return operations;
    }

    private static ProcessVariableOperation createOperationFromXml(XmlVariable variable)
            throws VariablesReaderException {
        final String name = variable.getName();
        final String process = variable.getProcess();
        String creationPoint = variable.getCreationPoint();
        String scope = variable.getScope();

        if (name == null || process == null) {
            throw new VariablesReaderException("Name or process id is not set.");
        }

        if (creationPoint == null) {
            creationPoint = "StartEvent";
        }

        if (scope == null) {
            scope = process;
        }

        ProcessVariableOperation operation = new ProcessVariableOperation(name, ElementChapter.UserDefined, KnownElementFieldType.UserDefined,
                VariableOperation.WRITE, scope);
        return operation;

    }

}
