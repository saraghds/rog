package com.buzzfuzz.rog.decisions;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.buzzfuzz.rog.decisions.ConfigTree.Scope;
import com.buzzfuzz.rog.decisions.Target;

public class Config {
	
	ConfigTree config;
	StringBuilder log;
	
	public Config() {
		config = new ConfigTree();
		log = new StringBuilder();
	}

	public void log(String msg) {
		log.append(msg);
	}

	public StringBuilder getLog() {
		return log;
    }
	
	@Override
    public int hashCode() {
        return config.hashCode();
    }

	public void addPair(Target target, Constraint constraint) {
		config.addPair(target, constraint);
	}

	public Constraint findConstraintFor(Target target) {
		return config.findPairFor(target, config.getRoot()).x.y.y;
	}
	
	private static void setAttribute(Document doc, Element parent, String name, String value) {
		Element elem = doc.createElement(name);
	    elem.setTextContent(value);
	    parent.appendChild(elem);
	}
	
	// TODO: These should be moved out to a utility class
	private static void appendScopes(Document doc, Element elem, Scope parent) {
		
		// Add target if it exists
		if (parent.getTarget() != null) {
			Target target = parent.getTarget();
			Element xmlTarget = doc.createElement("target");
			
			if (target.getInstancePath() != null)
				setAttribute(doc, xmlTarget, "instancePath", target.getInstancePath());
			if (target.getMethodPath() != null)
				setAttribute(doc, xmlTarget, "methodPath", target.getMethodPath());
			
			elem.appendChild(xmlTarget);
		}
		
		// Add constraint if it exists
		if (parent.getConstraint() != null) {
			Constraint constraint = parent.getConstraint();
			Element xmlConstraint = doc.createElement("constraint");
			
			if (constraint.getNullProb() != null)
				setAttribute(doc, xmlConstraint, "nullProb", constraint.getNullProb().toString());
			if (constraint.getProb() != null)
				setAttribute(doc, xmlConstraint, "prob", constraint.getProb().toString());
			
			elem.appendChild(xmlConstraint);
		}
			
		// Recursively add children
		if (parent.getChildren().size() > 0) {
			Element xmlScopes = doc.createElement("scopes");
			
			for (Scope child : parent.getChildren()) {
				Element childScope = doc.createElement("scope");
				appendScopes(doc, childScope, child);
				xmlScopes.appendChild(childScope);
			}
			
			elem.appendChild(xmlScopes);
		}
	}
	
	public Document toXML() {
		Document doc = null;
		try {
		    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		    // root elements
		    doc = docBuilder.newDocument();
		    Element rootElement = doc.createElement("config");
		    doc.appendChild(rootElement);
		    
		    appendScopes(doc, rootElement, config.getRoot());

		  } catch (ParserConfigurationException pce) {
		    pce.printStackTrace();
		  }
		return doc;
	}

}