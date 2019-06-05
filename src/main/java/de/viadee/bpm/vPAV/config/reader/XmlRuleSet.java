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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Collection;

@XmlRootElement(name = "ruleSet")
public class XmlRuleSet {

  private Collection<XmlRule> rules;
  private Collection<XmlRule> elementRules;
  private Collection<XmlRule> modelRules;

  public XmlRuleSet() {
  }

  // Todo delete this in future - will break backwards compatibility
  public XmlRuleSet(Collection<XmlRule> rules) {
    super();
    this.rules = rules;
    this.elementRules = new ArrayList<>();
    this.modelRules = new ArrayList<>();
  }

  public XmlRuleSet(Collection<XmlRule> elementRules, Collection<XmlRule> modelRules) {
    super();
    this.rules = new ArrayList<>();
    this.elementRules = elementRules;
    this.modelRules = modelRules;
  }

  // Todo delete this in future - will break backwards compatibility
  @XmlElement(name = "rule", type = XmlRule.class)
  public Collection<XmlRule> getRules() {
    return rules;
  }

  @XmlElementWrapper(name = "elementRules")
  @XmlElement(name = "rule", type = XmlRule.class)
  public Collection<XmlRule> getElementRules() {
    return elementRules;
  }

  @XmlElementWrapper(name = "modelRules")
  @XmlElement(name = "rule", type = XmlRule.class)
  public Collection<XmlRule> getModelRules() {
    return modelRules;
  }

  public void setElementRules(Collection<XmlRule> rules) {
    this.elementRules = rules;
  }
  public void setModelRules(Collection<XmlRule> rules) {
    this.modelRules = rules;
  }
}
