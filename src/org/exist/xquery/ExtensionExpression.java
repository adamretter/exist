/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import java.util.ArrayList;
import java.util.List;

import org.exist.dom.DocumentSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * Implements an XQuery extension expression. An extension expression starts with
 * a list of pragmas, followed by an expression enclosed in curly braces. For evaluation
 * details check {{@link #eval(Sequence, Item)}.
 * 
 * @author wolf
 *
 */
public class ExtensionExpression extends AbstractExpression {

    private Expression innerExpression;
    private List pragmas = new ArrayList(3);
    
    public ExtensionExpression(XQueryContext context) {
        super(context);
    }

    public void setExpression(Expression inner) {
        this.innerExpression = inner;
    }
    
    public void addPragma(Pragma pragma) {
        pragmas.add(pragma);
    }
    
    /**
     * For every pragma in the list, calls {@link Pragma#before(Expression) before evaluation.
     * The method then tries to call {@link Pragma#eval(Sequence, Item)} on every pragma.
     * If a pragma does not return null for this call, the returned Sequence will become the result
     * of the extension expression. If more than one pragma returns something for eval, an exception
     * will be thrown. If all pragmas return null, we call eval on the original expression and return
     * that.
     */
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
        callBefore();
        Sequence result = null;
        for (int i = 0; i < pragmas.size(); i++) {
            Pragma pragma = (Pragma) pragmas.get(i);
            Sequence temp = pragma.eval(contextSequence, contextItem);
            if (temp != null) {
                if (result != null)
                    throw new XPathException(getASTNode(), "Conflicting pragmas: only one should return a result for eval");
                result = temp;
            }
        }
        
        if (result == null)
            result = innerExpression.eval(contextSequence, contextItem);
        callAfter();
        return result;
    }

    private void callAfter() throws XPathException {
        for (int i = 0; i < pragmas.size(); i++) {
            Pragma pragma = (Pragma) pragmas.get(i);
            pragma.after(context, innerExpression);
        }
    }

    private void callBefore() throws XPathException {
        for (int i = 0; i < pragmas.size(); i++) {
            Pragma pragma = (Pragma) pragmas.get(i);
            pragma.before(context, innerExpression);
        }
    }

    public int returnsType() {
        return innerExpression.returnsType();
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        innerExpression.analyze(contextInfo);
    }

    public void dump(ExpressionDumper dumper) {
        for (int i = 0; i < pragmas.size(); i++) {
            Pragma pragma = (Pragma) pragmas.get(i);
            dumper.nl().display("(# " + pragma.getQName().toString(), getASTNode());
            if (pragma.getContents() != null)
                dumper.display(' ').display(pragma.getContents());
            dumper.display("#)").nl();
        }
        dumper.display('{').nl();
        dumper.startIndent();
        dumper.dump(innerExpression);
        dumper.endIndent();
        dumper.display('}').nl();
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#getDependencies()
     */
    public int getDependencies() {
        return innerExpression.getDependencies();
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#getCardinality()
     */
    public int getCardinality() {
        return innerExpression.getCardinality();
    }
    
    public void setContextDocSet(DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        innerExpression.setContextDocSet(contextSet);
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#resetState()
     */
    public void resetState() {
        super.resetState();
        innerExpression.resetState();
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visit(innerExpression);
    }
}
