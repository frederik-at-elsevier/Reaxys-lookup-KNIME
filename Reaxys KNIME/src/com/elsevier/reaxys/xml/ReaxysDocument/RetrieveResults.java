package com.elsevier.reaxys.xml.ReaxysDocument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.elsevier.reaxys.Cache;
import com.elsevier.reaxys.ReaxysDataTypes;
import com.elsevier.reaxys.ReaxysFieldTypes;

import com.elsevier.reaxys.xml.ReaxysDocument.ReaxysDocument;

/**
 * class to encapsulate the Reaxys result set. This stores the number of hits
 * and citations to allow easier retrieval.  
 * 
 * Now caches the keys and values to canonical values to save significant memory when the same
 * key (xml tag) and value (e.g. author list) is repeated for many records.
 * 
 * @author clarkm
 * @author TOM W
 * 
 */
public class RetrieveResults extends ReaxysDocument {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1848094335249958492L;
	private String resultStatus = null;  // status of results set
	private String resultName = null;  // hitset for results
	private String dbname = null;  // database name for results
	private int resultsize = 0;
	private boolean sd_v3 = false;
	
	/*
	 * separates journal citations. this could be a multi-character string
	 */
	final static  String CITATION_SEPARATOR = "|";
	/*
	 * separates multiple values in a field.  This could be a multi-character string
	 */
	final static String MULITPLE_VALUE_SEPARATOR = "|";
	/*
	 * if this is "citations" (plural) it will return all citations for the record as a single string
	 * in without the "s" it returns the fielded data for the citation.
	 */
	final static String CITATION_MARKER = "citation";
	
	// precompile this column name matcher for tiny performance increase.
	final static Pattern columnMatcher = Pattern.compile("^[A-Z]{2,4}[0][0-9].*|CIT|citation.");
	
	/*
	 * top level data types; parents of all other data types
	 */
	final String[] topLevel = {"RX", "RY", "IDE", "CIT", "DAT", "TARGET", "SUPL", "DATIDS"};

	/*
	 * cache in an attempt to reduce memory by canonicalizing strings.
	 */
	final Cache<String, String> resultCache = new Cache<String, String>();
	
	/**
	 * return number of hits from this search
	 * @return count of hits
	 */
	public int size() {
		return resultsize;
	}
	public String resName() {
		return resultName;
	}
	public String resStatus() {
		return resultStatus;
	}

	
	/**
	 * create the object and initialize with some description of the results
	 * 
	 * @param resp
	 */
	public RetrieveResults(final ReaxysDocument factory, final Document resp, final boolean sd_v3) {

		super(factory);
		XPathFactory xPathFactory = XPathFactory.newInstance();
		XPath xPath = xPathFactory.newXPath();
		String xPathExpression = "//result/status";
		try {
			XPathExpression expr = xPath.compile(xPathExpression);
			resultStatus = expr.evaluate(resp);
		} catch (XPathExpressionException e) {
			
		}
		

		
		resultName = getString("resultname", resp);
		dbname = getString("dbname", resp);
		
		this.sd_v3 = sd_v3;
		
		try {
			resultsize = Integer.parseInt(getString("resultsize", resp));
		} catch (final Exception e) {
			resultsize = 0;
		}
	}

	
	/**
	 * create the object and initialize with some description of the results
	 * 
	 * @param resp
	 */
	public RetrieveResults(final RetrieveResults factory, final Document resp) {

		super(factory);
		XPathFactory xPathFactory = XPathFactory.newInstance();
		XPath xPath = xPathFactory.newXPath();
		String xPathExpression = "//result/status";
		try {
			XPathExpression expr = xPath.compile(xPathExpression);
			resultStatus = expr.evaluate(resp);
		} catch (XPathExpressionException e) {
			
		}
		resultName = getString("resultname", resp);
		this.sd_v3 = factory.sd_v3;

		try {
			resultsize = Integer.parseInt(getString("resultsize", resp));
		} catch (final Exception e) {
			resultsize = 0;
		}
	}

	
	/**
	 * get status of V3000 SD file option
	 */
	public boolean getSD_V3() {
		return sd_v3;
	}
	
	/**
	 * look at request and see if we are looking for some facts.
	 * @param doc  result document, which repeats the search.
	 * 
	 * @return true if facts are being requested.
	 */
	boolean findFacts(Document doc) {
		
		NodeList selects = doc.getElementsByTagName("select_item");
		for (int i = 0; i < selects.getLength(); i++) {
			Node item = selects.item(i);
			// facts have pattern XXX(N1,N2)
			String value = item.getTextContent();
			if (value != null && value.contains("(")) {
				return true;
			}
		}
		
		return false;
	}
	
	
	/**
	 * Create an XML document to retrieve the desired values from the Reaxys hitset. 
	 * There is a limit of 100 values for first-last request for facts.
	 * 
	 * @param value  value to return from Reaxys
	 * @param type  type of data
	 * @param first - index of first value to retrieve
	 * @param last  - index of last value to retrieve
	 * @param option - extra option for query
	 * @return Document with query to retrieve values.
	 */
	public Document retrieveValues(final String value, ReaxysDataTypes type,
			final int first, final int last) {

		assert  (last - first) < 100: "Number of values requested greater than 100";

		final Document retrievalQuery = createRetrieveDocument();
		
		createElement(retrievalQuery, "select_list", "select_item");

		if (value != null) {
			
			String prefix = value;
			String suffix = "";
			
			try {
				prefix = value.substring(0, value.indexOf("("));
				suffix = value.substring(value.indexOf("("));
			} catch (Exception e) {}
			
			// for these data types, remove the counts like (1,100) etc.
			String query = value;
			for (String tag : topLevel) {
				if (value.startsWith(tag + "(")) {
					query = prefix;
				}
			}

			setTextNode(retrievalQuery, "select_item", query);
			
			// get associated field groups from enumeration.  This will
			// often be the parent fields of the requested data, sometimes
			// sibling fields.
			final ReaxysDataTypes rdt = ReaxysDataTypes.getByCodeAndDatabase(prefix, type.getDatabase());
			
			String[] extra = null;
			if (rdt != null ) {
				extra = rdt.getAssociatedTypes();
			}
			
			if (extra != null) {
				for (String dataType : extra) {
					createElement(retrievalQuery, "select_list", "select_item");
					/*
					 * these are 'main data types', not facts.
					 */
					for (String tag : topLevel) {
						if (dataType.equals(tag)) {
							suffix = "";
						}
					}

					setTextNode(retrievalQuery, "select_item", dataType + suffix);
				}
			}
			
		} else {
			setTextNode(retrievalQuery, "select_item", "");
		}

		setAttribute(retrievalQuery, "from_clause", "resultname", resultName);
		setAttribute(retrievalQuery, "from_clause", "dbname", dbname);
		setAttribute(retrievalQuery, "from_clause", "first_item",
				Integer.toString(first));
		setAttribute(retrievalQuery, "from_clause", "last_item",
				Integer.toString(last));
		
		
		String options;
		/*
		 * set options to have only V2000 or V3000.  Oddly we specify the one that we don't want.
		 */
		if (sd_v3) {
			options = "OMIT_CIT,OMIT_V2000,ISSUE_RXN=true";
		} else {
			options = "OMIT_CIT,OMIT_V3000,ISSUE_RXN=true";
		}
		
		setTextNode(retrievalQuery, "options", options);

		return retrievalQuery;
	}


	/**
	 * find out what kind of data is being returned.
	 * 
	 * @return
	 */
	final String findResultCategory(Document document) {
		
		final String[] categories = {"citations", "substances", "dpitems", "reactions", "tgitems"};
		final String context = getString("context", document);
		
		for (String category : categories) {
			if (context != null && context.equals(category)) {
				// trim off final 's' for individual records.
				return category.substring(0, category.length() - 1);
			}
		}
		return null;
	}
	
	
	/**
	 * get the first child node index that is an element node. This skips over
	 * text and other nodes to find the "meat".
	 * 
	 * @param node to search for children
	 * @return index of first element node
	 */
	int getFirstElement(final Node start) {
		
		NodeList list = start.getChildNodes();
		for (int i = 0; i < list.getLength(); i++) {
			if (list.item(i).getNodeType() == Node.ELEMENT_NODE) {
				return i;
			}
		}
		return -1;
	}
	
	int getFirstTopLevelElement(final Node start) {
			
		NodeList list = start.getChildNodes();
		for (int i = 0; i < list.getLength(); i++) {
			Node item = list.item(i);
			if (item.getNodeType() == Node.ELEMENT_NODE) {
				for (String t : topLevel) {
					if (item.getNodeName().equals(t)) {
						return i;
					}
				}
				
			}
		}
		
		return -1;
	}
	
	/**
	 * get a value from a document. returns all sub-tags from the given tag name
	 * 
	 * @param tagName   tag
	 * @param element   document
	 * @return value    ArrayList of Hashmaps
	 */
	final public  ArrayList<HashMap<String, String>> getResults(Document doc) {

		final ArrayList<HashMap<String, String>> result = new ArrayList<HashMap<String, String>>();
		/**
		 * if true, then the parent record is duplicated and added the subsequent data sections
		 * in this category.  e.g. the reaction details are combined with the parent reaction
		 * so each detail+parent is a complete record.
		 */
		boolean duplicateParent = true;
		final boolean lookingForFacts = findFacts(doc);

		String category = findResultCategory(doc);
		// empty data set; no results
		if (category == null) {
			return result;
		}
		// System.out.println("** category: " + category);
		
		if (category.equals("citation") || category.equals("dpitem")) {
			duplicateParent = false;
		}
		
		/*
		 * list of citations, substances, dpitems, or reactions
		 */
		final NodeList categoryList = doc.getElementsByTagName(category);
		/*
		 *  loop over all of the data sections in this category -
		 *  reaction, details, etc.
		 */
		for (int j = 0; j < categoryList.getLength(); j++) {  // each item
			final NodeList dataList = categoryList.item(j).getChildNodes();
			final HashMap<String, String> mainMap = new HashMap<String, String>();
			int main = getFirstTopLevelElement(categoryList.item(j)); // the the main item here
			if (dataList.item(main).equals("RY")) duplicateParent = false;
			parseData(dataList.item(main), mainMap);

			boolean addedMore = false; // flag to note there is more than one data section in this category

			// start after main section.
			for (int i = 0; i < dataList.getLength(); i++) {
				
				final Node currentNode = dataList.item(i);
				if (currentNode.getNodeType() != Node.ELEMENT_NODE  || i == main ) {
					continue;
				}

				HashMap<String, String> map = new HashMap<String, String>();
				/*
				 * under here there may be several associated items grouped together, e.g.
				 * rxn then several other data items associated with that RXN.
				 */
				
				if (duplicateParent) {
					map.putAll(mainMap);
				} else {
					map = mainMap;
				}

				parseData(currentNode, map);
				addedMore = true;
				/*
				 * sometimes data is mapped many-to-one (reactions)
				 * sometimes all subrecords are combined (citations)
				 */
				if (duplicateParent) {
					result.add(map);
				}
			}
			// if no facts were found, and I was looking for them, don't add this data
			// if I wasn't looking for facts, and didn't find any it is ok to add.
			if ((!duplicateParent || !addedMore) && !(!addedMore && lookingForFacts)) {
				result.add(mainMap);
			} /*else {
				System.out.println("** didn't add map of size " + mainMap.size() 
					+ " addedMore " + addedMore + " duplicateParent " + duplicateParent 
					+ " lookingForFacts " + lookingForFacts);
			} */
		}
		
		return result;
	}
	
	/**
	 * parse the node and return the tag-value pairs in a hashmap
	 * 
	 * @param root node to process
	 * @param map hashmap with tag/value pairs, which will be augmented with more pairs
	 * @return the hash map, with additional pairs
	 */
	final HashMap<String, String> parseData(final Node root, final HashMap<String, String> map) {
		
		final NodeList children = root.getChildNodes();
		if (children.getLength() == 0) return map;
		
		// loop thru sibling nodes
		for (Node n2 = children.item(0); n2 != null; n2 = n2.getNextSibling()) {
			
			if (n2.getNodeType() != Node.ELEMENT_NODE) continue;
			
			if (n2.hasChildNodes()) {
				
				String name = n2.getNodeName();

				if (name != null) {
					name = ReaxysFieldTypes.getLabel(name.trim());
				}

				String value = n2.getTextContent().replaceAll("\\s+$","");// trim trailing newlines only not .trim();

				/*
				 * special case for citations, concatenate all the fields and insert separator
				 */
				if (value != null && name.equals("citation")) {
					// separate the citation elements with pipe
					//value = value.replaceAll("\\n\\s+", CITATION_SEPARATOR);
					parseData(n2, map);
				} else

				/*
				 * special case because RMC has fields with subfields DAT01, DAT02, IDE01 etc.
				 */
				if (value != null && (columnMatcher.matcher(name).matches())) {
					parseData(n2, map);
				} else {
					// if there are multiple instances, concatenate them unless it is an IDE value so that
					// the XRN and other numbers are not repeated.
					// other values, like melting points may be repeated.
					if (map.containsKey(name) && !name.contains("Reaxys Registry Number") && !map.get(name).equals(value)) {
						value = map.get(name) + MULITPLE_VALUE_SEPARATOR + value;
					}
					
					// Special case for the RY context: get the RX.ID from the RY.STR element attribute
					if (name.contains("RY.STR")) {
					    NamedNodeMap attrMap = n2.getAttributes();
					    String rxid = attrMap.getNamedItem("rn").getNodeValue();
					    if (rxid == null) {
					        rxid = "";
                        }
					    map.put(ReaxysFieldTypes.getLabel("RX.ID").intern(), resultCache.canon(rxid));
					}

					map.put(name.intern(), resultCache.canon(value));
				}
			}
		}

		return map;
	}
}