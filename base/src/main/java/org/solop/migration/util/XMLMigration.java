/**
 * 
 */
package org.solop.migration.util;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MMigration;
import org.compiere.model.MMigrationData;
import org.compiere.model.MMigrationStep;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Trx;
import org.spin.util.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 *    @author Yamel Senih, yamel.senih@solopsoftware.com, Solop <a href="http://www.solopsoftware.com">solopsoftware.com</a>
 */
public class XMLMigration implements IMigrationManagement {

	private final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	private final CLogger log = CLogger.getCLogger(this.getClass());

	public XMLMigration() {
		dbf.setNamespaceAware(true);
		dbf.setIgnoringElementContentWhitespace(true);
		try {
			XMLUtils.setDefaultFeatures(dbf);
		} catch (Exception e) {
			log.log(Level.SEVERE, e.getLocalizedMessage());
		}
	}

	public void exportMigration(Properties context, int migrationId, String fileName, String transactionName) {
		MMigration migration = new MMigration(context, migrationId, transactionName);
		try {
			Document document = null;
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			document = builder.newDocument();
			Element root = document.createElement("Migrations");
			document.appendChild(root);
			root.appendChild(migration.toXmlNode(document));
			//set up a transformer
			TransformerFactory transformer = TransformerFactory.newInstance();
			transformer.setAttribute("indent-number", 2);
			Transformer trans;
			trans = transformer.newTransformer();
			trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			trans.setOutputProperty(OutputKeys.INDENT, "yes");
			trans.setOutputProperty(OutputKeys.STANDALONE, "yes");
			FileWriter fw = new FileWriter(fileName);
			StreamResult result = new StreamResult(fw);
			DOMSource source = new DOMSource(document);
			trans.transform(source, result);
			fw.close();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			throw new AdempiereException(e);
		}
	}

	@Override
	public String getExtension() {
		return "xml";
	}

	@Override
	public List<Integer> importMigration(List<File> files, MigrationFinder finder, String transactionName) {
		if(files == null || files.isEmpty()) {
			return List.of();
		}
		//	Files are loaded in parallel: the map must be thread safe or entries are lost
		Map<String, Integer> migrationsToApply = new ConcurrentHashMap<>();
		files.parallelStream().forEach(file ->{
			try {
				int migrationId = loadFile(file, finder);
				if(migrationId > 0) {
					migrationsToApply.put(file.getAbsolutePath(), migrationId);
				}
			} catch (Exception e) {
				log.log(Level.SEVERE, "Error loading " + file + ": " + e.getLocalizedMessage(), e);
			}
		});
		return migrationsToApply.keySet().stream()
				.sorted()
				.map(migrationsToApply::get)
				.collect(Collectors.toList());
	}

	private int loadFile(File file, MigrationFinder finder) {
		AtomicInteger migrationReferenceId = new AtomicInteger(-1);
		try {
			if (!file.exists() || !file.getName().endsWith(".xml") || file.getName().equals("build.xml")) {
				return -1;
			}
			log.log(Level.CONFIG, "Loading file: " + file);
			Document doc = dbf.newDocumentBuilder().parse(file);
			NodeList migrations = doc.getDocumentElement().getElementsByTagName("Migration");
			if(migrations.getLength() > 0) {
				Element element = (Element) migrations.item(0);
				Properties ctx = Env.getCtx();
				try {
					//	The migration, its steps and their data are saved in one transaction: when any of them
					//	fails nothing is registered, instead of leaving a migration with only its first steps
					//	(it would be skipped by the next run because it is already registered)
					Trx.run(transactionName -> {
						MMigration migration = saveMigrationFromXmlNode(ctx, element, finder, transactionName);
						if(migration != null) {
							migrationReferenceId.set(migration.getAD_Migration_ID());
						}
					});
				} catch (Exception e) {
					migrationReferenceId.set(-1);
					log.log(Level.SEVERE, "Migration not imported from " + file + ": " + e.getLocalizedMessage(), e);
				}
			}
		} catch (Exception e) {
			log.log(Level.SEVERE, e.getLocalizedMessage());
		}
		return migrationReferenceId.get();
	}

	private MMigration saveMigrationFromXmlNode(Properties ctx, Element element, MigrationFinder finder, String transactionName) {
		if ( !"Migration".equals(element.getLocalName() ) ) {
			return null;
		}
		String name = element.getAttribute("Name").trim();
		String seqNo = element.getAttribute("SeqNo").trim();
		String entityType = element.getAttribute("EntityType").trim();
		String releaseNo = element.getAttribute("ReleaseNo").trim();
		if(finder.existsMigration(entityType, releaseNo, name, Integer.parseInt(seqNo))) {
			return null;
		}
		MMigration migration = new MMigration(ctx, 0, transactionName);
		migration.setName(name);
		migration.setSeqNo(Integer.parseInt(seqNo));
		migration.setEntityType(entityType);
		migration.setReleaseNo(releaseNo);
		Node comment = element.getElementsByTagName("Comments").item(0);
		if (comment != null) {
			migration.setComments(comment.getTextContent());
		}
		migration.saveEx();
		NodeList children = element.getElementsByTagName("Step");
		Stream<Node> nodeStream = IntStream.range(0, children.getLength())
				.mapToObj(children::item);
		//	A step that fails aborts the whole migration (the transaction is rolled back)
		nodeStream.forEach(elementValue -> {
			Element step = (Element) elementValue;
			if ( "Step".equals(step.getTagName())) {
				try {
					saveStepFromXmlNode(ctx, migration.getAD_Migration_ID(), step, transactionName);
				} catch (Exception e) {
					throw new AdempiereException(migration + ": Step " + step.getAttribute("SeqNo") + " not imported: " + e.getLocalizedMessage(), e);
				}
			}
		});
		return migration;
	}

	private void saveStepFromXmlNode(Properties ctx, int migrationId, Node stepNode, String transactionName) {
		MMigrationStep migrationStep = new MMigrationStep(ctx, 0, transactionName);
		migrationStep.setAD_Migration_ID(migrationId);
		Element step = (Element) stepNode;
		migrationStep.setSeqNo(Integer.parseInt(step.getAttribute("SeqNo")));
		migrationStep.setStepType(step.getAttribute("StepType"));
		migrationStep.setStatusCode(MMigrationStep.STATUSCODE_Unapplied);
		migrationStep.setIsDirectLoad(true);
		migrationStep.saveEx();
		Node comment = (Element) step.getElementsByTagName("Comments").item(0);
		if (comment != null) {
			migrationStep.setComments(comment.getTextContent());
		}
		if(MMigrationStep.STEPTYPE_ApplicationDictionary.equals(migrationStep.getStepType())) {
			NodeList children = step.getElementsByTagName("PO");
			for ( int i = 0; i < children.getLength(); i++ ) {
				Element element = (Element) children.item(i);
				migrationStep.setAction(element.getAttribute("Action"));
				migrationStep.setAD_Table_ID(Integer.parseInt(element.getAttribute("AD_Table_ID")));
				migrationStep.setRecord_ID(Integer.parseInt(element.getAttribute("Record_ID")));
				NodeList data = element.getElementsByTagName("Data");
				for (int j =0 ; j < data.getLength(); j++) {
					saveNodeFromXmlNode(ctx, migrationStep.getAD_MigrationStep_ID(), data.item(j), transactionName);
				}
			}
		} else if (MMigrationStep.STEPTYPE_SQLStatement.equals(migrationStep.getStepType())) {
			migrationStep.setDBType(step.getAttribute("DBType"));

			// If Parse is defined, set it accordingly, else, use the default or ignore
			if (!step.getAttribute("Parse").isEmpty()) {
				migrationStep.setParse("Y".equals(step.getAttribute("Parse")));
			}

			Node sql = step.getElementsByTagName("SQLStatement").item(0);
			if ( sql != null )
				migrationStep.setSQLStatement(sql.getTextContent());
			sql = step.getElementsByTagName("RollbackStatement").item(0);
			if ( sql != null )
				migrationStep.setRollbackStatement(sql.getTextContent());
		}
		migrationStep.setIsDirectLoad(true);
		migrationStep.saveEx();
		log.log(Level.CONFIG, migrationStep.getAD_Migration().toString() + ": Step " + migrationStep.getSeqNo() + " loaded");
	}

	private void saveNodeFromXmlNode(Properties ctx, int stepId, Node item, String transactionName) {
		MMigrationData data = new MMigrationData(ctx, 0, transactionName);
		data.setAD_MigrationStep_ID(stepId);
		Element element = (Element)item;
		data.setIsOldNull("true".equals(element.getAttribute("isOldNull")));
		data.setOldValue(element.getAttribute("oldValue"));
		data.setAD_Column_ID(Integer.parseInt(element.getAttribute("AD_Column_ID")));
		data.setIsNewNull("true".equals(element.getAttribute("isNewNull")));
		data.setNewValue(element.getTextContent());
		data.setIsDirectLoad(true);
		data.saveEx();
	}

	@Override
	public String toString() {
		return "XMLMigration: " + getExtension();
	}
}