/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */

package org.exist.indexing.lucene;

import org.exist.util.XMLString;

public abstract class AbstractTextExtractor implements TextExtractor {

    protected LuceneConfig config;
    protected LuceneConfigText idxConfig;
    protected int initLevel = 0;

    protected XMLString buffer = new XMLString();

    public void configure(LuceneConfig config, LuceneConfigText idxConfig, int level) {
        this.config = config;
        this.idxConfig = idxConfig;
        
        initLevel = level;
    }

    public LuceneConfigText getIndexConfig() {
    	return idxConfig;
    }
    
    public int getInitLevel() {
    	return initLevel;
    }

    public XMLString getText() {
        return buffer;
    }
}
