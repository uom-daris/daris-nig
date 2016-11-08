package nig.mf.plugin.pssd.ni;

import arc.mf.plugin.PluginService;
import arc.mf.plugin.dtype.AssetType;
import arc.mf.plugin.dtype.BooleanType;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;


public class SvcMBCFMPUploads extends PluginService {
	// Relative location of resource file on server
	private static final String FMP_CRED_REL_PATH = "/.fmp/petct_fmpcheck";
	private static final String EMAIL = "williamsr@unimelb.edu.au";
	//private static final String EMAIL = "nkilleen@unimelb.edu.au";

	private Interface _defn;

	public SvcMBCFMPUploads() throws Throwable {

		_defn = new Interface();
		//

		Interface.Element me = new Interface.Element(
				"cid",
				CiteableIdType.DEFAULT,
				"The citeable ID of the parent Study holding the SR DICOM modality DataSet.",
				0, 1);

		_defn.add(me);
		//
		me = new Interface.Element("id", AssetType.DEFAULT, "The asset ID (not citeable) of the parent Study.",
				0, 1);
		_defn.add(me);
		//
		me = new Interface.Element("email", StringType.DEFAULT, "The destination email address to send a report to if an exception occurs. Over-rides any built in one.",
				0, 1);
		_defn.add(me);
		//
		me = new Interface.Element("update", BooleanType.DEFAULT, "Actually update FMP with the new values. Defaults to false.",
				0, 1);
		_defn.add(me);
		//
		me = new Interface.Element("force", BooleanType.DEFAULT, "Over-ride the meta-data on the Study indicating that this Study has already been checked, and check regardless. Defaults to false.",
				0, 1);
		_defn.add(me);
		
        _defn.add(new Interface.Element("no-email", BooleanType.DEFAULT,
                "Do not send email. Defaults to false.", 0, 1));

	}

	@Override
	public String name() {
		return "nig.pssd.mbic.fmp.uploads";
	}

	@Override
	public String description() {

		return "Runs nig.pssd.mbic.petvar.check and nig.pssd.mbic.dose.upload sequentially. These upload values into MBC IU FMP. See the services for details";
	}

	@Override
	public Interface definition() {

		return _defn;
	}

	@Override
	public Access access() {

		return ACCESS_ADMINISTER;
	}

	@Override
	public boolean canBeAborted() {

		return false;
	}

	@Override
	public void execute(XmlDoc.Element args, Inputs in, Outputs out, XmlWriter w) throws Throwable {

		// Parse input ID
		Boolean update = args.booleanValue("update", false);
		String cid = args.value("cid");
		String id = args.value("id");
		String email = args.stringValue("email", EMAIL);
		Boolean force = args.booleanValue("force", false);
        Boolean noEmail = args.booleanValue("no-email", null);
        
		// Do it
		XmlDocMaker dm = new XmlDocMaker("args");
		if (id!=null) dm.add("id", id);
		if (cid!=null) dm.add("cid", cid);
		dm.add("email", email);
		dm.add("force", force);
		dm.add("update", update);
		if(noEmail!=null){
		    dm.add("no-email", noEmail);
		}
		try {
			// PET/CT variables first
			w.push("nig.pssd.mbic.petvar.check");
			XmlDoc.Element r = executor().execute("nig.pssd.mbic.petvar.check", dm.root());
			w.addAll(r.elements());
			w.pop();
			
			// SR dose uploads 
			w.push("nig.pssd.mbic.dose.upload");
			r = executor().execute("nig.pssd.mbic.dose.upload", dm.root());
			w.addAll(r.elements());
			w.pop();

		} catch (Throwable t) {
			// We dont want to run the dose uploads if the first service fails.
			throw new Exception (t);
		}


	}
}


