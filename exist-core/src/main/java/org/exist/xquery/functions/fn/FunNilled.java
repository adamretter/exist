/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.fn;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Node;

/**
 * Built-in function fn:nilled().
 * 
 * @author wolf
 */
public class FunNilled extends BasicFunction {

	private static final QName qName = new QName("nilled", Function.BUILTIN_FUNCTION_NS);
	private static final FunctionReturnSequenceType returns = FunctionDSL.returns(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the argument node is \"nilled\"");

	public static final FunctionSignature[] FUNCTION_SIGNATURES_NILLED = {

			FunctionDSL.functionSignature(
					FunNilled.qName,
					"Returns an xs:boolean indicating whether the argument node is \"nilled\". " +
							"If the argument is not an element node, returns the empty sequence. " +
							"If the argument is the empty sequence, returns the empty sequence.",
					FunNilled.returns,
					FunctionDSL.param("node", Type.NODE, Cardinality.ZERO_OR_MORE, "node to test")),
			FunctionDSL.functionSignature(
					FunNilled.qName,
					"Returns an xs:boolean indicating whether the default (context) item is \"nilled\". " +
							"If the context item is not an element node, returns the empty sequence. " +
							"If the context item is the empty sequence, returns the empty sequence.",
					FunNilled.returns)
	};

	public FunNilled(final XQueryContext context, final FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

		final Sequence item;
		if (args.length == 0) {
			item = contextSequence;
		} else {
			item = args[0];
		}

		if (item == null || item.isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}

		if (!Type.subTypeOf(item.getItemType(), Type.NODE)) {
			throw new XPathException(ErrorCodes.XPTY0004, "Type error in call to nilled. " + item + " type ( " + item.getItemType() + " ) is not a node type");
		}

		final Item arg = item.itemAt(0);
		if (!Type.subTypeOf(arg.getType(), Type.ELEMENT)) {
			return Sequence.EMPTY_SEQUENCE;
		}

		final Node n = ((NodeValue) arg).getNode();
		if (n.hasAttributes()) {
			final Node nilled = n.getAttributes().getNamedItemNS(Namespaces.SCHEMA_INSTANCE_NS, "nil");
			if (nilled != null) {
				return new BooleanValue(this, nilled.getNodeValue().equals("true"));
			}
		}

        return BooleanValue.FALSE;
		
	}

}
