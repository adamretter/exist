package org.exist.indexing.lucene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Field;
import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;

public class LuceneConfig {

	protected final static Logger LOG = Logger.getLogger(LuceneConfig.class);
	
	private Map<QName, LuceneConfigText> paths = new TreeMap<QName, LuceneConfigText>();
	private List<LuceneConfigText> wildcardPaths = new ArrayList<LuceneConfigText>();
	private Map<String, LuceneConfigText> namedIndexes = new TreeMap<String, LuceneConfigText>();
    
	protected Map<String, FieldType> fieldTypes = new HashMap<String, FieldType>();
    
	private Set<QName> inlineNodes = null;
	private Set<QName> ignoreNodes = null;

    private PathIterator iterator = new PathIterator();
    
    protected float boost = -1;

    protected AnalyzerConfig analyzers = new AnalyzerConfig();

    public LuceneConfig() {
    }

    /**
     * Copy constructor. LuceneConfig is only configured once by database instance,
     * so to avoid concurrency issues when using e.g. iterator, we create a copy.
     * 
     * @param other
     */
    public LuceneConfig(LuceneConfig other) {
    	this.paths = other.paths;
    	this.wildcardPaths = other.wildcardPaths;
    	this.namedIndexes = other.namedIndexes;
    	this.fieldTypes = other.fieldTypes;
    	this.inlineNodes = other.inlineNodes;
    	this.ignoreNodes = other.ignoreNodes;
    	this.boost = other.boost;
    	this.analyzers = other.analyzers;
    }
    
    public void add(LuceneConfigText config) {
		// if it is a named index, add it to the namedIndexes map
		if (config.getName() != null) {
			namedIndexes.put(config.getName(), config);
        }

		// register index either by QName or path
		if (config.getNodePath().hasWildcard()) {
			wildcardPaths.add(config);
		} else {
		    LuceneConfigText idxConf = paths.get(config.getNodePath().getLastComponent());
		    if (idxConf == null) {
		    	paths.put(config.getNodePath().getLastComponent(), config);
            }
		    else {
                idxConf.add(config);
            }
		}
	}
    
    public boolean matches(NodePath path) {
        LuceneConfigText idxConf = paths.get(path.getLastComponent());
        while (idxConf != null) {
            if (idxConf.match(path))
                return true;
            idxConf = idxConf.getNext();
        }
        for (LuceneConfigText config : wildcardPaths) {
            if (config.match(path))
                return true;
        }
        return false;
    }

    public Iterator<LuceneConfigText> getConfig(NodePath path) {
        iterator.reset(path);
        return iterator;
    }

    protected LuceneConfigText getWildcardConfig(NodePath path) {
        LuceneConfigText config;
        for (int i = 0; i < wildcardPaths.size(); i++) {
            config = wildcardPaths.get(i);
            if (config.match(path))
                return config;
        }
        return null;
    }

    public Analyzer getAnalyzer(QName qname) {
        LuceneConfigText idxConf = paths.get(qname);
        while (idxConf != null) {
            if (!idxConf.isNamed() && idxConf.getNodePath().match(qname))
                break;
            idxConf = idxConf.getNext();
        }
        if (idxConf != null) {
            String id = idxConf.getAnalyzerId();
            if (id != null)
                return analyzers.getAnalyzerById(idxConf.getAnalyzerId());
        }
        return analyzers.getDefaultAnalyzer();
    }

    public Analyzer getAnalyzer(NodePath nodePath) {
        if (nodePath.length() == 0)
            throw new RuntimeException();
        LuceneConfigText idxConf = paths.get(nodePath.getLastComponent());
        while (idxConf != null) {
            if (!idxConf.isNamed() && idxConf.match(nodePath))
                break;
            idxConf = idxConf.getNext();
        }
        if (idxConf == null) {
            for (LuceneConfigText config : wildcardPaths) {
                if (config.match(nodePath))
                    return config.getAnalyzer();
            }
        }
        if (idxConf != null) {
            String id = idxConf.getAnalyzerId();
            if (id != null)
                return analyzers.getAnalyzerById(idxConf.getAnalyzerId());
        }
        return analyzers.getDefaultAnalyzer();
    }

    public Analyzer getAnalyzer(String field) {
        LuceneConfigText config = namedIndexes.get(field);
        if (config != null) {
            String id = config.getAnalyzerId();
            if (id != null)
                return analyzers.getAnalyzerById(config.getAnalyzerId());
        }
        return analyzers.getDefaultAnalyzer();
    }

    public Analyzer getAnalyzerById(String id) {
    	return analyzers.getAnalyzerById(id);
    }
    
	public void addInlineNode(QName qname) {
	    if (inlineNodes == null)
	    	inlineNodes = new TreeSet<QName>();

	    inlineNodes.add(qname);
	}

	public boolean isInlineNode(QName qname) {
        return inlineNodes != null && inlineNodes.contains(qname);
    }
	
	public void addIgnoreNode(QName qname) {
	    if (ignoreNodes == null)
	    	ignoreNodes = new TreeSet<QName>();

	    ignoreNodes.add(qname);
	}

    public boolean isIgnoredNode(QName qname) {
        return ignoreNodes != null && ignoreNodes.contains(qname);
    }

    public float getBoost() {
        return boost;
    }
    
    public void addFieldType(FieldType type) {
    	fieldTypes.put(type.getId(), type);
	}
    
    public FieldType getFieldType(String name){
        return fieldTypes.get(name);
    }

    private class PathIterator implements Iterator<LuceneConfigText> {

        private LuceneConfigText nextConfig;
        private NodePath path;
        private boolean atLast = false;

        protected void reset(NodePath path) {
            this.atLast = false;
            this.path = path;
            nextConfig = paths.get(path.getLastComponent());
            if (nextConfig == null) {
                nextConfig = getWildcardConfig(path);
                atLast = true;
            }
        }

        @Override
        public boolean hasNext() {
            return (nextConfig != null);
        }

        @Override
        public LuceneConfigText next() {
            if (nextConfig == null)
                return null;

            LuceneConfigText currentConfig = nextConfig;
            nextConfig = nextConfig.getNext();
            if (nextConfig == null && !atLast) {
                nextConfig = getWildcardConfig(path);
                atLast = true;
            }
            return currentConfig;
        }

        @Override
        public void remove() {
            //Nothing to do
        }

    }
}
