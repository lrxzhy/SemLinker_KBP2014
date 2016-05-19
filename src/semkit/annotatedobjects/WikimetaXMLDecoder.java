package semkit.annotatedobjects;

/*

SemLinker 2014

Copyright (C) 2014  Marie-Jean Meurs & Hayda Almeida
                    Ludovic Jean-Louis & Eric Charton

Copyright (C) 2013  Eric Charton & Marie-Jean Meurs &
                    Ludovic Jean-Louis & Michel Gagnon

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, 
Boston, MA  02110-1301, USA.

Contacts :

This software is maintained and released at:

https://github.com/SemLinker-Team/SemLinker_KBP2014

Please contact respective authors from this page for support
or any inquiries. 

 */

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import kbp2014.managedocuments.LanguageSpellChecker;

import org.apache.commons.lang.StringUtils;

import com.sun.org.apache.xpath.internal.axes.HasPositionalPredChecker;


/**
 * 
 * This class builds an object that represents an annotated document
 * retrieved from Wikimeta API under an XML form. </br>
 * </br>
 * Note that this class allows to retrieve more that 3 candidates as
 * the public version of the Wikimeta API does. This has been introduced
 * for the NIST KBP2013 campaign where the specific Wikimeta Server
 * used can return 15 candidates. 
 * 
 * @author ericcharton
 *
 */
public class WikimetaXMLDecoder implements AnnotationInterface {


	String ontologyDomain = "wikimeta.com";
	// index by line of CDATA
	private HashMap<Integer, String> WordEntries = new HashMap<Integer, String>();
	private HashMap<Integer, String> WordEntriesNormalized = new HashMap<Integer, String>();
	private HashMap<Integer, String> POSEntries = new HashMap<Integer, String>();
	private HashMap<Integer, String> NEEntries = new HashMap<Integer, String>();
	
	/**
	 * Maps to hold original offset of  
	 * each word and the document ID (HA)
	 */
	private HashMap<Integer, Integer> BegOffset = new HashMap<Integer, Integer>();
	private HashMap<Integer, Integer> EndOffset = new HashMap<Integer, Integer>();
	private HashMap<Integer, String> DocID = new HashMap<Integer, String>();
	private HashMap<Integer, String> Replace = new HashMap<Integer, String>();
	private OffsetFinder offsetFinder = new OffsetFinder();
	
	private int maxwords = 0; // number of lines in the annotations objects

	// Creating new HashMap objects 
	// Transformation in hash of hashes for variable key length (in process)
	// hashmap <linenumber < orderNumberofKey, key>>
	//  -- key is the line number
	//  -- Hash in ash is a list ordered of keys
	private HashMap<Integer, HashMap<Integer, String>> MetadataUriByLine = new HashMap<Integer, HashMap<Integer, String>>();
	private int maxannotationcandidates = 6; // the number of annotations retrieved from API


	private HashMap<Integer, String> LINKEDDATA = new HashMap<Integer, String>();
	private HashMap<Integer, String> fullSequence = new HashMap<Integer, String>(); // the sequence annotated
	private HashMap<Integer, String> fullSequenceNormalized = new HashMap<Integer, String>(); // the sequence annotated

	/**
	 * 
	 * Build a representation of an object according to an XML 
	 * sent by Wikimeta API. This class only collect the informations from CDATA (do not need all
	 * the XML structure of the API return). 
	 * 
	 * @param XMLtext The XML returned by Wikimeta API
	 * @param numberofcandidates The number of candidates used from the CDATA section
	 * @return 
	 */
	public void decoder (String XMLtext, String rawText, int numberofcandidates, LanguageSpellChecker spellchecker){

		maxannotationcandidates = numberofcandidates;

		String textarray[] = XMLtext.split("\n");
		
		String documentID = ""; 
		if(StringUtils.containsIgnoreCase(rawText,"<DOCID>")) documentID = StringUtils.substringBetween(rawText, "<DOCID> ", " </DOCID>");
		if(StringUtils.contains(rawText, "doc id")) documentID = StringUtils.substringBetween(rawText, "<doc id=\"", "\"");
		if(StringUtils.contains(rawText, "DOC id")) documentID = StringUtils.substringBetween(rawText, "<DOC id=\"", "\"");		

		// collect cdata
		for (int x = 0; x < textarray.length; x++){

			if (textarray[x].contains("<![CDATA[")){

				// explore the cdata section
				int y = x + 1; // go just after CDATA
				int marker = 0;
				/**
				 * Counter to keep track of last word checked 
				 * on original text. It serves as starting 
				 * point when searching for next word (HA).
				 */
				int origTextCounter = 0;
				String punctuations = ".,;:";
				while(! textarray[y].contains("]]>")){

					String[] linextract = textarray[y].split("\t");
					
					/**
					 * holds the name of annotated entity (HA)
					 */					
					String currentEntity = linextract[0];
					String originalText = rawText;
					int[] originalPosition = new int[2];
					
					
					// introduce main entries
					try{
						WordEntries.put(marker, linextract[0]);
						POSEntries.put(marker, linextract[1]);
						NEEntries.put(marker, linextract[2].toUpperCase());
						
						/**
						 * Find the offset of annotation on original text 
						 * (HA).
						 */						
						if(/*!Pattern.matches("\\p{Punct}", currentEntity) 
								&& */ Pattern.matches("^(.*?[a-zA-Z]){1,}.*$", currentEntity) 
								&& currentEntity.length() > 1) {
						//if( !(punctuations.contains(currentEntity)) && (currentEntity.length() == 1)) {							
						//	if(!(currentEntity.replaceAll(punctuations, "").matches("^[0-9]+$"))){
								originalPosition = offsetFinder.findOriginalOffset(origTextCounter, currentEntity, originalText, spellchecker);
							//}
							if(originalPosition[1] > 0) origTextCounter = originalPosition[1];
						}
					//	}

																		
						/**
						 * Inserting offsets and doc ID 
						 * on appropriate lists (HA). 
						 */						
						BegOffset.put(marker, originalPosition[0]);
						EndOffset.put(marker, originalPosition[1]);
						DocID.put(marker, documentID);

						// create a normalized entry
						WordEntriesNormalized.put(marker, linextract[0].toLowerCase());

					}catch (Exception e ){

						// catch exception for some errors that can happen in long document aggregation
						System.out.println(" !!ERROR in XMLDEcoder!! [" + textarray[y] + "]" + y + " " + textarray.length);
						WordEntries.put(marker, "Error");
						POSEntries.put(marker, "ERR");
						NEEntries.put(marker, "UNK");
						
						/**
						 * (HA) 
						 */
						BegOffset.put(marker, 0);
						EndOffset.put(marker, 0);
						DocID.put(marker, "");

					}

					// collect n metadata
					// 3 is the first one
					// the dbpedia and confidence (5)
					// 6 is the 2d one
					// 7 is the 3d one

					// Store n annotations for this line -> marker
					HashMap<Integer, String> MetadataUriToStore = new HashMap<Integer, String>(); // build temporary hashmap	


					if ( linextract.length > 3 ){
						if ( linextract[3].contains(ontologyDomain) ) 
						{ 

							MetadataUriToStore.put(0, linextract[3]);

						} else 
						{
							if (!linextract[2].contains("TIME") && !linextract[2].contains("AMOUNT")) {

								MetadataUriToStore.put(0, "NIL");
							}
						}
					}

					// collect the other output
					// put in line Hash from 1 to 5
					for ( int orderNum = 1 ; orderNum < maxannotationcandidates; orderNum++){
						// if there are data at given rank, then store it in hash
						if ( linextract.length > orderNum + 5 ){
							if ( linextract[orderNum + 5].contains(ontologyDomain) ) {
								MetadataUriToStore.put(orderNum,linextract[orderNum +5]);
							}
						}
					}


					// store the temporary Hash in global store
					MetadataUriByLine.put(marker, MetadataUriToStore);



					// collect full ref of reference entity
					// we begin with metadata of ref 0 (best score) and search the span
					if ( MetadataUriToStore.containsKey(0)){

						int beginOfSpan = y + 1;
						String StrSpanOfEnt = linextract[0];
						// the search the next -> while lines contain same NE
						while(textarray[beginOfSpan].contains(linextract[2]) ){
							// System.out.println("------------->" + textarray[beginOfSpan]);
							String[] nextSFComponent = textarray[beginOfSpan].split("\t");
							StrSpanOfEnt = StrSpanOfEnt + " " + nextSFComponent[0];

							// safety
							if (textarray[beginOfSpan].contains("]]>") ) break;

							beginOfSpan++;
						}
						//StrSpanOfEnt = StrSpanOfEnt.replace("\n", " ");						
						fullSequence.put(marker, StrSpanOfEnt);
						fullSequenceNormalized.put(marker, StrSpanOfEnt.toLowerCase());
					}

					// update values describing the annotation object
					marker++;
					maxwords++;

					// update indexes
					y++;

				}
				break; // once CDATA viewed, halt
			}

		}

	}

	/** 
	 * Clean the XML format
	 * 
	 * http%3A%2F%2Fwww.dbpedia.org%2Fresource%2FDuPont
	 * http://www.december.com/html/spec/esccodes.html
	 */
	private String revertXMLformat(String format){

		format = format.replaceAll("%3A", ":");
		format = format.replaceAll("%2F", "/");
		format = format.replaceAll("%3F", "?");
		format = format.replaceAll("%26", "&");
		format = format.replaceAll("%3D", "=");

		return(format);
	}

	/** 
	 * 
	 * Return a specifically ranked metadata key
	 * 
	 * @param rank
	 * @param linenumber
	 * @return
	 */
	public String getMetadataKeyRanked (int rank, int linenumber){

		String uriToReturn = null;
		String metakey =  null;
		uriToReturn = getmetadataNotTransformed(rank, linenumber);

		if (uriToReturn != null){
			metakey = uriToReturn;
			metakey = metakey.replaceAll("http://wikimeta.com/perl/display.pl\\?query=", ""); // note here -> perl instead of wapi, bug to be fixed in semtag
			metakey = metakey.replaceAll("http://wikimeta.com/wapi/display.pl\\?query=", ""); // note here -> perl instead of wapi, bug to be fixed in semtag
			metakey = metakey.replaceAll("&search=EN", ""); // optional
		}

		return(metakey);
	}



	/**
	 * 
	 * 
	 * 
	 * @param rank
	 * @param linenumber
	 * @return
	 */
	private String getmetadataNotTransformed(int rank, int linenumber){

		String uriToReturn = null;
		boolean collected = false;
		// collect the Hash for this line
		HashMap<Integer, String> MetadataUriforthisline = new HashMap<Integer, String>(); // build temporary hashmap
		MetadataUriforthisline = MetadataUriByLine.get(linenumber); // collect the line

		if (MetadataUriforthisline != null){
			if  (MetadataUriforthisline.containsKey(rank) ) { 

				uriToReturn = MetadataUriforthisline.get(rank);
				collected = true;
			}
		}

		// verify if this is not empty
		// metadata error in API return:
		// http://wikimeta.com/perl/display.pl?query=&search=EN
		if (collected && uriToReturn != null && ! uriToReturn.contentEquals("")) { 
			if (uriToReturn.contains("query=&search")) return null; 
		}

		return(uriToReturn);

	}
	
	
	/**
	 * Return the amount of word entries in the document. The size is equal to the number
	 * of lines where one line contains one word, punctuation or number. 
	 * 
	 * 
	 * @return
	 */
	public int size(){

		return(maxwords);   
	}



	/**
	 * 
	 * Methods to get a NE label at a pos if exists
	 * 
	 * @return NE label in a string
	 */
	public String getNELabel (int linenumber){

		if  (NEEntries.containsKey(linenumber)) { 

			return( NEEntries.get(linenumber));

		}
		else{
			return(null); // no NE at this POS
		}
	}

	/**
	 * 
	 * Methods to get the NLGBASE URI descriptor
	 * 
	 * @return Metadata as a URI in a string according to 
	 * line number for first rank
	 * 
	 */
	public String getMetadata (int linenumber){

		return( getMetadataRanked (0, linenumber)); // no NE at this POS
	}

	/**
	 * 
	 * 
	 * @param linenumber
	 * @returnMetadata as a key (equivalent to Wikipedia
	 * key) in a string according to line number for first rank
	 * 
	 */
	public String getMetadatakey (int linenumber){

		return(getMetadataKeyRanked (0, linenumber)); // no NE at this POS
	}

	/** 
	 * 
	 * Return a specifically ranked metadata
	 * 
	 * @param rank
	 * @param linenumber
	 * @return
	 */
	public String getMetadataRanked (int rank, int linenumber){

		return(getmetadataNotTransformed(rank, linenumber));
	}


	

	/** Methods to get the LinkedData URI descriptor
	 * 
	 * @param linenumber
	 * @return
	 */
	public String getLinkedData (int linenumber){

		if  (LINKEDDATA.containsKey(linenumber)) { 

			return( LINKEDDATA.get(linenumber));

		}
		else{
			return(null); // no NE at this POS
		}
	}

	/**
	 * 
	 * Return the word at a given position
	 * 
	 * @param linenumber
	 * @return
	 */
	public String getWordAtPos (int linenumber){


		if  (WordEntries.containsKey(linenumber)) { 

			return( WordEntries.get(linenumber));

		}
		else{
			return(null); // no NE at this POS
		}

	}


	/**
	 * 
	 * Return the POS at a given position
	 * 
	 * @param linenumber
	 * @return
	 */
	public String getPOSatpos (int linenumber){


		if  (POSEntries.containsKey(linenumber)) { 

			return( POSEntries.get(linenumber));

		}
		else{
			return(null); // no NE at this POS
		}

	}

	/**
	 * 
	 * @param linenumber
	 * @return
	 */
	public String getSurfaceFormAtPos (int linenumber){


		if  (fullSequence.containsKey(linenumber)) { 

			return( fullSequence.get(linenumber));

		}
		else{
			return(null); // no NE at this POS
		}

	}


	/**
	 * 
	 * @param linenumber
	 * @return
	 */
	public String getSurfaceFormNormalizedAtPos (int linenumber){


		if  (fullSequenceNormalized.containsKey(linenumber)) { 

			return( fullSequenceNormalized.get(linenumber));

		}
		else{
			return(null); // no NE at this POS
		}

	}
		

	// -----------------------------------------------
	//  Replacement methods
	//------------------------------------------------

	public void setNElabel(int linenumber, String en){

		NEEntries.put(linenumber, en);

	}

	/**
	 * 
	 * Set a surfaceform at given position
	 * 
	 * @param linenumber 	This is the linenumber where to set the sf
	 * @param Sf 			This is the surface form to set
	 * @param lenght		This is the length of the SF (reserved for future use to set the NE)
	 */
	public void setSfAtPos(int linenumber, String Sf, int lenght){

		fullSequence.put(linenumber, Sf);
		fullSequenceNormalized.put(linenumber, Sf.toLowerCase());
	}

	/**
	 * 
	 * @param linenumber
	 */
	public void removeSfAtPos(int linenumber){

		fullSequence.remove(linenumber);
		fullSequenceNormalized.remove(linenumber);
	}

	/**
	 * 
	 * @param linenumber
	 * @param metadata
	 */
	public void setMetadataWithUri(int linenumber, String metadata){

		setMetadatarankedWithUri(linenumber, metadata, 0);

	}

	/**
	 * 
	 * @param linenumber
	 * @param metadata
	 * @param rank
	 */
	public void setMetadatarankedWithUri(int linenumber, String metadata, int rank){

		// collect the previous line content to update it
		HashMap<Integer, String> MetadataUriToStore = new HashMap<Integer, String>(); // build temporary hashmap
		MetadataUriToStore = MetadataUriByLine.get(linenumber); // collect the line

		// set or reset the key
		MetadataUriToStore.put(rank, metadata);
		// reset the the key at line
		MetadataUriByLine.put(linenumber, MetadataUriToStore);

	}

	/**
	 * 
	 * @param linenumber
	 * @param metadata
	 */
	public void setMetadataWithKey(int linenumber, String metadata){

		setMetadatarankedWithKey(linenumber, metadata, 0);

	}

	/**
	 * 
	 * @param linenumber
	 * @param metadata
	 * @param rank
	 */
	public void setMetadatarankedWithKey(int linenumber, String metadata, int rank){

		HashMap<Integer, String> MetadataUriToStore = new HashMap<Integer, String>(); // build temporary hashmap
		MetadataUriToStore = MetadataUriByLine.get(linenumber); // collect the line

		// re-build the URI -> later: replace by variablemetalinks.put(annotations.getMetadatakey(h), 1);
		if ( metadata != null){	
			if ( ! metadata.contentEquals("NIL") ){
				metadata = "http://wikimeta.com/wapi/display.pl?query=" + metadata + "&search=EN";
			}
		}

		// set or reset the key
		MetadataUriToStore.put(rank, metadata);
		// refill the global table
		MetadataUriByLine.put(linenumber, MetadataUriToStore);

	}
	
	/**
	 * Return the beginning position of a given 
	 * word in the original doc (HA)
	 * 
	 * @param linenumber
	 * @return
	 */
	public String getDocID(int linenumber){
		if(DocID.containsKey(linenumber)){
			return (DocID.get(linenumber));
		}
		else return "";
	}
	
	/**
	 * Return the beginning position of a given 
	 * word in the original doc (HA)
	 * 
	 * @param linenumber
	 * @return
	 */

	public int getBeginOffset (int linenumber){
		
		if(BegOffset.containsKey(linenumber)){
			
			return(BegOffset.get(linenumber));
		}
		else return 0;		
	}
	
	/**
	 * Return the ending position of a given 
	 * word in the original doc (HA)
	 * 
	 * @param linenumber
	 * @return
	 */
	public int getEndOffset (int linenumber){
		
		if(EndOffset.containsKey(linenumber)){
			
			return(EndOffset.get(linenumber));
		}
		else return 0;		
	}
	
	public void setEndOffset(int linenumber, int offset){
		EndOffset.put(linenumber, offset);
	}
	
	public void setBeginOffset(int linenumber, int offset){
		BegOffset.put(linenumber, offset);
	}
	
    /**
	 * Return the position of a given 
	 * word in the annotation object (HA)
	 * 
	 * @param mention  word to be searched
	 * @return
	 */
	public int getAnnotatedPosition(String mention){
		int value = 0;
		for(Entry<Integer,String> entries : WordEntries.entrySet()){
			if(entries.getValue().equalsIgnoreCase(mention)){
				value = entries.getKey();
			}
			
		}
		return value;
					
	}
	
	public String toString(int i){
		return "Annotation word: " + getWordAtPos(i) + "\n" +   
				"NE: "+ getNELabel(i) + "\n" + 				   
			    "BeginOffset: " + getBeginOffset(i) + "\n" +
				"EndOffSet: " + getEndOffset (i) + "\n" +
				"Metadata: " + getMetadata(i) + "\n" +
				"-------------------------------- \n"; 		
	}
		
}
