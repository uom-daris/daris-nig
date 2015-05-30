package nig.mf.plugin.pssd.ni;

import java.util.Collection;

import nig.mf.dicom.plugin.util.DICOMPatient;
import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import nig.mf.pssd.plugin.util.PSSDUtil;

import arc.mf.plugin.PluginService;

import arc.mf.plugin.PluginService.Interface.Element;
import arc.mf.plugin.dtype.BooleanType;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;

/**
 * Checks names match in DICOM and other meta-data
 * 
 * @author nebk
 * 
 */
public class SvcProjectNameCheck extends PluginService {
	private Interface _defn;


	public SvcProjectNameCheck() {

		_defn = new Interface();
		_defn.add(new Element("pid", CiteableIdType.DEFAULT, "The citeable asset id of the Project to check.",
				1, 1));
		_defn.add(new Element("all", BooleanType.DEFAULT, "Examine all DataSets. It defaults to the first DataSet per STudy.", 0, 1));
		_defn.add(new Element("list-all", BooleanType.DEFAULT, "List DataSets whether names match or not. Defaults to false (only list ones that don't match). all DataSets.", 0, 1));
	}

	public String name() {
		return "nig.pssd.project.name.check";
	}

	public String description() {
		return "Checks that human names held in indexed meta-data on Subjects match that of the DICOM and Siemens raw file meta-data held in child DataSets.";
	}

	public Interface definition() {
		return _defn;
	}

	public Access access() {
		return ACCESS_ADMINISTER;
	}

	public void execute(XmlDoc.Element args, Inputs in, Outputs out, XmlWriter w) throws Throwable {
		// Must be system-admin 
		nig.mf.pssd.plugin.util.PSSDUtil.isSystemAdministrator(executor());

		// Get project IDs and validate
		String pid = args.value("pid");	
		PSSDUtil.isValidProject(executor(), pid, true);
		Boolean all = args.booleanValue("all", false);
		Boolean list = args.booleanValue("list-all", false);

		// FInd Subjects
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", pid);
		XmlDoc.Element r = executor().execute("om.pssd.collection.member.list", dm.root());
		if (r==null) return;
		Collection<String> subjects = r.values("object/id");
		if (subjects==null) return;

		// Iterate
		for (String subject : subjects) {

			// Get name meta-data
			XmlDoc.Element meta = AssetUtil.getAsset(executor(), subject, null);

			// DICOM
			XmlDoc.Element dicomPatient = meta.element("asset/meta/mf-dicom-patient");
			DICOMPatient dp = new DICOMPatient(dicomPatient);
			String dicomName = dp.getFullName();
			String dicomFirstName = dp.getFirstName();
			String dicomLastName = dp.getLastName();

			// nig identity
			XmlDoc.Element humanIdentity = meta.element("asset/meta/nig-daris:pssd-human-identity");
			String identityName = null;
			if (humanIdentity!=null) {
				identityName = humanIdentity.value("first") + " " + humanIdentity.value("last");
			}		
			w.push("subject", new String[]{"id", subject, "mf-dicom-patient", dicomName, "nig-daris:pssd-human-identity", identityName});

			// Find Studies
			dm = new XmlDocMaker("args");
			dm.add("where", "cid starts with '" + subject + "' and model='om.pssd.study'");
			dm.add("pdist", 0);
			r = executor().execute("asset.query", dm.root());
			if (r==null) return;
			Collection<String> studies  = r.values("id");  // Asset IDs
			if (studies==null) return;

			// Iterate 
			for (String study : studies) {
				String studyCID = CiteableIdUtil.idToCid(executor(), study);

				// FInd DataSets
				dm = new XmlDocMaker("args");
				dm.add("id", studyCID);
				r = executor().execute("om.pssd.collection.member.list", dm.root());
				if (r==null) return;
				Collection<String> dataSets  = r.values("object/id");
				if (dataSets==null) return;

				// Iterate
				int i = 0;
				for (String dataSet : dataSets) {
					if (all || i==0) {
						XmlDoc.Element dsMeta = AssetUtil.getAsset(executor(), dataSet, null);
						String assetId = CiteableIdUtil.cidToId(executor(), dataSet);
						String type = dsMeta.value("asset/type");
						if (type.equals("dicom/series")) {
							dm = new XmlDocMaker ("args");
							dm.add("id", assetId);
							XmlDoc.Element dicomMeta = executor().execute("dicom.metadata.get", dm.root());
							XmlDoc.Element t = dicomMeta.element("de[@tag='00100010']");
							String value = null;
							String firstName = null;
							String lastName = null;
							if (t!=null) {
								value = t.value("value");
								String t2[] = value.split(" ");

								// Compare
								if (t2.length==2) {
									firstName = t2[0];
									lastName = t2[1];
								} else if (t2.length==1) {
									firstName = value;
								}
							}
							boolean good = false;
							if (dicomFirstName!=null && dicomLastName !=null && firstName!=null && lastName!=null) {
								if (dicomFirstName.equalsIgnoreCase(firstName) && dicomLastName.equalsIgnoreCase(lastName)) good = true;
							}
							if (!good||list) w.add("dataset", new String[]{"id", dataSet, "type", "dicom", "name", value});
						} else if (type.equals("siemens-raw-pet/series") || type.equals("siemens-raw-ct/series")) {
							String source = dsMeta.value("asset/meta/daris:siemens-raw-petct-series /source");
							String firstName = null;
							String lastName = null;
							boolean good = false;
							String fullName = null;
							if (source!=null) {
								String t[] = source.split("\\.");
								String t2[] = t[0].split("_");
								if (t2.length==2) {
									firstName = t2[1];
									lastName = t2[0];
									fullName = firstName + " " + lastName;
								} else if (t2.length==1) {
									lastName = t2[0];
									fullName = lastName;
								}
								if (dicomFirstName!=null && dicomLastName !=null && firstName!=null && lastName!=null) {
									if (dicomFirstName.equalsIgnoreCase(firstName) && dicomLastName.equalsIgnoreCase(lastName)) good = true;
								}
		
							}
							if (fullName == null) fullName = source;
							if (!good||list) w.add("dataset", new String[]{"id", dataSet, "type", "raw", "name", fullName});
						}
						i++;
					}
				}
			}
			w.pop();
		}
	}
}
