package nig.mf.plugin.pssd.ni;

import java.util.Date;

import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import nig.util.DateUtil;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.dtype.AssetType;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;

public class SvcMBCPETHasRaw extends PluginService {
	private Interface _defn;

	public SvcMBCPETHasRaw() throws Throwable {

		_defn = new Interface();
		Interface.Element me = new Interface.Element("cid",
				CiteableIdType.DEFAULT,
				"The identity of the parent Study.  All child DataSets (and in a federation children will be found on all peers in the federation) containing DICOM data will be found and sent.",
				0, 1);

		_defn.add(me);
		//
		me = new Interface.Element("id", AssetType.DEFAULT,
				"The asset identity (not citable ID) of the parent Study.", 0,
				1);
		_defn.add(me);
		//
		me = new Interface.Element("email", StringType.DEFAULT,
				"A destination email address to send a standard message to if there is raw data. Over-rides any built in one.",
				0, 1);
		_defn.add(me);
		me = new Interface.Element("body", StringType.DEFAULT,
				"Additional message to be included with the standard message.",
				0, 1);
		_defn.add(me);
	}

	@Override
	public String name() {
		return "nig.pssd.mbic.pet.raw.has";
	}

	@Override
	public String description() {
		return "Given a DICOM Study asset, tries to establish if raw data has been uploaded for it. It does this purely by date (no time).";
	}

	@Override
	public Interface definition() {

		return _defn;
	}

	@Override
	public Access access() {

		return ACCESS_ACCESS;
	}

	@Override
	public boolean canBeAborted() {

		return false;
	}

	@Override
	public void execute(XmlDoc.Element args, Inputs in, Outputs out,
			XmlWriter w) throws Throwable {

		// Parse input ID
		String studyID = args.value("id");
		String studyCID = args.value("cid");
		String email = args.stringValue("email"); 
		if (studyID != null && studyCID != null) {
			throw new Exception("Can't supply 'id' and 'cid'");
		}
		if (studyID == null && studyCID == null) {
			throw new Exception("Must supply 'id' or 'cid'");
		}
		if (studyCID == null) {
			studyCID = CiteableIdUtil.idToCid(executor(), studyID);
		}
		String body2 = args.stringValue("body");


		// Get the Study
		XmlDoc.Element studyMeta = AssetUtil.getAsset(executor(), studyCID, null);
		String model = studyMeta.value("asset/model");
		if (model == null
				|| (model != null && !model.equals("om.pssd.study"))) {
			throw new Exception("Supplied parent is not a Study");
		}
		//
		XmlDoc.Element dicom = studyMeta.element("asset/meta/mf-dicom-study");
		if (dicom==null) {
			throw new Exception("Supplied Study is not DICOM");
		}

		// Get date of acquisition
		Date sdate = dicom.dateValue("sdate");
		String date = DateUtil.formatDate(sdate, false, false);

		// See if we can find a raw study with this date
		String exMethodCID = CiteableIdUtil.getExMethodId(studyCID);
		w.add("dicom-id", studyCID);
		w.add("dicom-date", date);

		//
		XmlDocMaker dm = new XmlDocMaker("args");
		String where = "cid starts with '" + exMethodCID + "' and model='om.pssd.study' and " + 
				"xpath(daris:siemens-raw-petct-study) has value";
		dm.add("where", where);
		XmlDoc.Element r = executor().execute("asset.query", dm.root());
		String id = r.value("id");
		if (id==null) {
			w.add("raw-id", "none");
			if(email!=null){
				send(executor(), email, studyCID, date, body2);
			}
		} else {
			String cid = CiteableIdUtil.idToCid(executor(), id);
			w.add("raw-id", cid);
		}
	}


	private void send(ServiceExecutor executor, String email, String cid, String date, String body2)
			throws Throwable {
		String subject = "DICOM Study '" + cid + "' has no raw data";
		String body = "Dear person \n \n The DICOM Study with CID '" + cid + "'\n" +
				"and date of acquisition '" + date + "'\n" +
				"does not appear to have any associated Siemens PET/CT raw data. \n";
		if (body2!=null) {
			body = body + "\n" + body2 + "\n";
		}
		body = body + "\n We are very sorry but you probably bave only yourself to blame. \n" +
				"\n\n regards \n DaRIS";
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("async", "true");
		dm.add("to", email);
		dm.add("body", body);
		dm.add("subject", subject);
		executor.execute("mail.send", dm.root());
	}

}