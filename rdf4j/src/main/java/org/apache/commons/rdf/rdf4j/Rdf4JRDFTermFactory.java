/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.rdf.rdf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;


// To avoid confusion, avoid importing 
// classes that are in both
// commons.rdf and openrdf.model (e.g. IRI)
import org.apache.commons.rdf.api.BlankNode;
import org.apache.commons.rdf.api.BlankNodeOrIRI;
import org.apache.commons.rdf.api.RDFTerm;
import org.apache.commons.rdf.api.RDFTermFactory;
import org.apache.commons.rdf.api.Triple;
import org.apache.commons.rdf.simple.Types;
import org.openrdf.model.BNode;
import org.openrdf.model.Model;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.LinkedHashModel;
import org.openrdf.model.impl.SimpleValueFactory;
import org.openrdf.rio.turtle.TurtleUtil;

public class Rdf4JRDFTermFactory implements RDFTermFactory {
	
	private abstract class RDFTermImplementation<T extends Value> implements RDFTerm {
		T value;

		public T asValue() { 
			return value;
		}
		
		RDFTermImplementation(T value) {
			this.value = value;			
		}
	}
	
	private final class GraphImplementation implements org.apache.commons.rdf.api.Graph {
		
		private Model model;

		public Model asModel() { 
			return model;
		}
		
		public GraphImplementation(Model model) {
			this.model = model;		
		}
		
		@Override
		public long size() {
			int size = model.size();
			if (size < Integer.MAX_VALUE) {
				return size;
			} else {
				// Collection.size() can't help us, we'll have to count
				return model.parallelStream().count();
			}				
		}

		@Override
		public void remove(BlankNodeOrIRI subject, org.apache.commons.rdf.api.IRI predicate, RDFTerm object) {
			model.remove(
					(Resource)asValue(subject), 
					(org.openrdf.model.IRI)asValue(predicate), 
					asValue(object));
			
		}

		@Override
		public void remove(Triple triple) { 
			remove(triple.getSubject(), triple.getPredicate(), triple.getObject());
			
			// This however fails:
			// model.remove(asStatement(triple));
		}

		@Override
		public Stream<? extends Triple> getTriples(BlankNodeOrIRI subject, org.apache.commons.rdf.api.IRI predicate, RDFTerm object) {
			return model.filter(
					(Resource)asValue(subject), 
					(org.openrdf.model.IRI)asValue(predicate), 
					asValue(object)).parallelStream()
				.map(Rdf4JRDFTermFactory.this::asTriple);
		}

		@Override
		public Stream<Triple> getTriples() {
			return model.parallelStream().map(Rdf4JRDFTermFactory.this::asTriple);
		}

		@Override
		public boolean contains(BlankNodeOrIRI subject, org.apache.commons.rdf.api.IRI predicate, RDFTerm object) {
			return model.contains(
					(Resource)asValue(subject), 
					(org.openrdf.model.IRI)asValue(predicate), 
					asValue(object));
		}

		@Override
		public boolean contains(Triple triple) {
			return model.contains(asStatement(triple));
		}

		@Override
		public void clear() {
			model.clear();
		}

		@Override
		public void add(BlankNodeOrIRI subject, org.apache.commons.rdf.api.IRI predicate, RDFTerm object) {
			model.add(
					(Resource)asValue(subject), 
					(org.openrdf.model.IRI)asValue(predicate), 
					asValue(object));				
		}

		@Override
		public void add(Triple triple) {
			model.add(asStatement(triple));
		}
	}

	private final class TripleImplementation implements Triple {
		private final Statement statement;

		private TripleImplementation(Statement statement) {
			this.statement = statement;
		}

		public Statement asStatement() { 
			return statement;
		}
		
		@Override
		public BlankNodeOrIRI getSubject() {
			return (BlankNodeOrIRI) asRDFTerm(statement.getSubject());
		}

		@Override
		public org.apache.commons.rdf.api.IRI getPredicate() {
			return (org.apache.commons.rdf.api.IRI) asRDFTerm(statement.getPredicate());
		}

		@Override
		public RDFTerm getObject() {
			return asRDFTerm(statement.getObject());
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Triple) {
				Triple triple = (Triple) obj;
				return getSubject().equals(triple.getSubject()) &&
						getPredicate().equals(triple.getPredicate()) && 
						getObject().equals(triple.getObject());
			}
			return false;
		}

		@Override
		public int hashCode() {
			return Objects.hash(getSubject(), getPredicate(), getObject());
		}
		
		@Override
		public String toString() {
			return statement.toString();
		}
	}

	private final class IRIImplementation extends RDFTermImplementation<org.openrdf.model.IRI> 
		implements org.apache.commons.rdf.api.IRI {

		public IRIImplementation(org.openrdf.model.IRI iri) {
			super(iri);			
		}
		@Override
		public String ntriplesString() {
			return "<" + value.toString() +  ">";
		}

		@Override
		public String getIRIString() {
			return value.toString();
		}

		@Override
		public String toString() {
			return value.toString();
		}

		public int hashCode() {
			// Same definition
			return value.hashCode();
		}
		@Override
		public boolean equals(Object obj) {
			if (obj == this) { return true; }
			if (obj instanceof IRIImplementation) {
				IRIImplementation impl = (IRIImplementation) obj; 
				return asValue().equals(impl.asValue());
			}
			if (obj instanceof org.apache.commons.rdf.api.IRI) {
				org.apache.commons.rdf.api.IRI iri = (org.apache.commons.rdf.api.IRI) obj;
				return value.toString().equals(iri.getIRIString());
			}
			return false;
		}
		
	}

	private final class LiteralImplementation 
		extends RDFTermImplementation<org.openrdf.model.Literal>
		implements org.apache.commons.rdf.api.Literal {

		public LiteralImplementation(org.openrdf.model.Literal literal) {
			super(literal);			
		}
		@Override
		public String ntriplesString() {
			// TODO: Use a more efficient StringBuffer
			String escaped = QUOTE + TurtleUtil.encodeString(value.getLabel()) + QUOTE;
			if (value.getLanguage().isPresent()) {
				return escaped + "@" + value.getLanguage();
			}
			if (getDatatype().equals(Types.XSD_STRING)) { 
				return escaped;
			}
			return escaped + "^^" + value.getDatatype();
		}

		@Override
		public String getLexicalForm() {
			return value.getLabel();
		}

		@Override
		public org.apache.commons.rdf.api.IRI getDatatype() {
			return (org.apache.commons.rdf.api.IRI) asRDFTerm(value.getDatatype());
		}

		@Override
		public Optional<String> getLanguageTag() {
			return value.getLanguage();
		}

		@Override
		public String toString() {
			return ntriplesString();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) { return true; }
			if (obj instanceof org.apache.commons.rdf.api.Literal) {
				org.apache.commons.rdf.api.Literal other = (org.apache.commons.rdf.api.Literal) obj;
				return getLexicalForm().equals(other.getLexicalForm()) &&
						getDatatype().equals(other.getDatatype()) && 
						getLanguageTag().equals(other.getLanguageTag());
				
			}
			return false;
		}

		public int hashCode() {
			return Objects.hash(value.getLabel(), value.getDatatype(), value.getLanguage());
		}
	}

	private final class BlankNodeImplementation extends RDFTermImplementation<BNode>
		implements BlankNode {
		
		public BlankNodeImplementation(BNode bNode) {
			super(bNode);			
		}
		
		@Override
		public String ntriplesString() {
			return "_:" + value.getID();
		}

		@Override
		public String uniqueReference() {
			return salt + value.getID();
		}

		@Override
		public int hashCode() {
			return uniqueReference().hashCode();
		}

		public boolean equals(Object obj) { 
			if (obj == this) { 
				return true;
			}
			// NOTE: Do NOT use Bnode.equals() as it has a more generous
			// equality based only on the value.getID();			
			if (obj instanceof BlankNode) {
				BlankNode blankNode = (BlankNode) obj;
				return uniqueReference().equals(blankNode.uniqueReference());								
			}
			return false;
		}
	}

	private static final String QUOTE = "\"";

	private ValueFactory valueFactory;
	
	private String salt = "urn:uuid:" + UUID.randomUUID() + "#";
	
	public Rdf4JRDFTermFactory() {
		this.valueFactory = SimpleValueFactory.getInstance();
	}
	
	public Rdf4JRDFTermFactory(ValueFactory valueFactory) { 
		this.valueFactory = valueFactory;
	}	
	
	@Override
	public BlankNode createBlankNode() throws UnsupportedOperationException {
		BNode bnode = valueFactory.createBNode();
		return (BlankNode)asRDFTerm(bnode);
	}
	
	@Override
	public BlankNode createBlankNode(String name) throws UnsupportedOperationException {
		BNode bnode = valueFactory.createBNode(name);
		return (BlankNode)asRDFTerm(bnode);
	}
	
	@Override
	public org.apache.commons.rdf.api.Literal createLiteral(String lexicalForm) throws IllegalArgumentException, UnsupportedOperationException {
		org.openrdf.model.Literal lit = valueFactory.createLiteral(lexicalForm);
		return (org.apache.commons.rdf.api.Literal)asRDFTerm(lit);
	}

	@Override
	public org.apache.commons.rdf.api.Literal createLiteral(String lexicalForm, org.apache.commons.rdf.api.IRI dataType)
			throws IllegalArgumentException, UnsupportedOperationException {
		org.openrdf.model.IRI iri = valueFactory.createIRI(dataType.getIRIString());
		org.openrdf.model.Literal lit = valueFactory.createLiteral(lexicalForm, iri);
		return (org.apache.commons.rdf.api.Literal)asRDFTerm(lit);
	}
	
	@Override
	public org.apache.commons.rdf.api.Literal createLiteral(String lexicalForm, String languageTag)
			throws IllegalArgumentException, UnsupportedOperationException {
		org.openrdf.model.Literal lit = valueFactory.createLiteral(lexicalForm, languageTag);
		return (org.apache.commons.rdf.api.Literal)asRDFTerm(lit);
	}
	
	@Override
	public org.apache.commons.rdf.api.IRI createIRI(String iri) throws IllegalArgumentException, UnsupportedOperationException {
		return (org.apache.commons.rdf.api.IRI) asRDFTerm(valueFactory.createIRI(iri));
	}
	
	@Override
	public org.apache.commons.rdf.api.Graph createGraph() throws UnsupportedOperationException {
		return asRDFTermGraph(new LinkedHashModel());
	}
	
	public Statement asStatement(Triple triple) {
		return valueFactory.createStatement(
				(org.openrdf.model.Resource) asValue(triple.getSubject()), 
				(org.openrdf.model.IRI) asValue(triple.getPredicate()), 
				asValue(triple.getObject()));
	}
	
	@Override
	public Triple createTriple(BlankNodeOrIRI subject, org.apache.commons.rdf.api.IRI predicate, RDFTerm object)
			throws IllegalArgumentException, UnsupportedOperationException {
		final Statement statement = valueFactory.createStatement(
				(org.openrdf.model.Resource) asValue(subject), 
				(org.openrdf.model.IRI) asValue(predicate), 
				asValue(object));
		return asTriple(statement);
	}	
	
	public Value asValue(RDFTerm object) {		
		if (object == null) { 
			return null;
		}
		if (object instanceof RDFTermImplementation) {
			// One of our own - avoid converting again.
			// (This is crucial to avoid double-escaping in BlankNode)
			return ((RDFTermImplementation<?>)object).asValue();
		}
		if (object instanceof org.apache.commons.rdf.api.IRI) {
			org.apache.commons.rdf.api.IRI iri = (org.apache.commons.rdf.api.IRI) object;
			return valueFactory.createIRI(iri.getIRIString());
		}
		if (object instanceof org.apache.commons.rdf.api.Literal) {
			org.apache.commons.rdf.api.Literal literal = (org.apache.commons.rdf.api.Literal) object;
			String label = literal.getLexicalForm();
			if (literal.getLanguageTag().isPresent()) {
				String lang = literal.getLanguageTag().get();
				return valueFactory.createLiteral(label, lang);
			}
			org.openrdf.model.IRI dataType = (org.openrdf.model.IRI ) asValue(literal.getDatatype());
			return valueFactory.createLiteral(label, dataType);
		}
		if (object instanceof BlankNode) {
			// This is where it gets tricky to support round trips!			
			BlankNode blankNode = (BlankNode) object;
			// FIXME: The uniqueReference might not be a valid BlankNode identifier..
			// does it have to be?
			return valueFactory.createBNode(blankNode.uniqueReference());
		}
		throw new IllegalArgumentException("RDFTerm was not an IRI, Literal or BlankNode: " + object.getClass());
	}

	public org.apache.commons.rdf.api.Graph asRDFTermGraph(Model model) {
		return new GraphImplementation(model);
	}

	public Triple asTriple(final Statement statement) {
		return new TripleImplementation(statement);
	}

	public RDFTerm asRDFTerm(final org.openrdf.model.Value value) {		
		if (value instanceof BNode) {
			return new BlankNodeImplementation((BNode) value);
		}
		if (value instanceof org.openrdf.model.Literal) {
			return new LiteralImplementation((org.openrdf.model.Literal) value);
		}
		if (value instanceof org.openrdf.model.IRI) {
			return new IRIImplementation((org.openrdf.model.IRI) value);
		}
		throw new IllegalArgumentException("Value is not a BNode, Literal or IRI: " + value.getClass());		
	}
	
}
