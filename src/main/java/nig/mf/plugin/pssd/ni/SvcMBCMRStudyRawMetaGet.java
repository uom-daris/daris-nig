package nig.mf.plugin.pssd.ni;

import java.util.Collection;
import java.util.Date;

import nig.io.LittleEndianDataInputStream;
import nig.mf.plugin.pssd.util.ni.MRMetaData;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import nig.util.DateUtil;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.PluginTask;
import arc.mf.plugin.PluginService.Interface.Element;
import arc.mf.plugin.dtype.BooleanType;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;


public class SvcMBCMRStudyRawMetaGet extends PluginService {

	private Interface _defn;

	public SvcMBCMRStudyRawMetaGet() {
		_defn = new Interface();
		_defn.add(new Element("cid", CiteableIdType.DEFAULT, "The citeable asset id of the parent object.", 0, 1));
		_defn.add(new Element("id", CiteableIdType.DEFAULT, "The  asset id of the parent object.", 0, 1));
		_defn.add(new Element("list-only", BooleanType.DEFAULT, "Just list the metadata (default false).", 0, 1));
	}

	public String name() {

		return "nig.pssd.mbic.mr.study.raw.metadata.get";
	}

	public String description() {

		return "For all raw Siemens MR Studies without the date field fetch the date from the first child data set and stick on daris:siemens-raw-mr-study/date.";
	}

	public Interface definition() {

		return _defn;
	}

	public Access access() {

		return ACCESS_ADMINISTER;
	}


	@Override
	public int minNumberOfOutputs() {
		return 0;
	}

	@Override
	public int maxNumberOfOutputs() {
		return 0;
	}

	public boolean canBeAborted() {

		return true;
	}

	public void execute(XmlDoc.Element args, Inputs arg1, Outputs arg2, XmlWriter w)
			throws Throwable {

		// Parse arguments
		String cid = args.stringValue("cid");
		String id = args.stringValue("id");
		if (cid==null && id==null) {
			throw new Exception ("You must supply 'id' or 'cid'");
		}
		if (cid!=null && id!=null) {
			throw new Exception ("You must supply 'id' or 'cid'");
		}
		String pid= cid;
		if (id!=null) {
			pid = CiteableIdUtil.idToCid(executor(), id);
		}
		Boolean listOnly = args.booleanValue("list-only",  false);
		//
		// Find raw studies
		XmlDocMaker dm = new XmlDocMaker("args");
		//		String where = "(cid='" + pid +"' or cid starts with '" + pid + 
		//				"') and model='om.pssd.study' and daris:siemens-raw-mr-study has value and xpath(daris:siemens-raw-mr-study/date) hasno value";
		String where = "(cid='" + pid +"' or cid starts with '" + pid + 
				"') and model='om.pssd.study' and daris:siemens-raw-mr-study has value";
		dm.add("where", where);
		dm.add("size", "infinity");
		dm.add("pdist", "0");
		XmlDoc.Element r = executor().execute("asset.query", dm.root());
		if (r==null) return;
		//
		Collection<String> studyIDs = r.values("id");
		if (studyIDs==null) return;
		for (String studyID : studyIDs) {
			w.push("study");
			PluginTask.checkIfThreadTaskAborted();
			String studyCID = CiteableIdUtil.idToCid(executor(), studyID);
			w.add("id", studyCID);
			// For the STudy, find the first raw data file
			dm = new XmlDocMaker("args");
			where = "cid starts with '" + studyCID + "' and model='om.pssd.dataset' and type='siemens-raw-mr/series'";
			dm.add("where", where);
			dm.add("size", "infinity");
			dm.add("pdist", "0");
			r = executor().execute("asset.query", dm.root());
			if (r==null) return;

			// Fetch the first data set
			Collection<String> dataSetIDs = r.values("id");
			Boolean first = true;
			Boolean same = true;
			String frameOfReferenceAll = null;
			for (String dataSetID : dataSetIDs) {
				PluginService.Outputs outputs = new PluginService.Outputs(1);
				w.push("data-set");
				w.add("id", CiteableIdUtil.idToCid(executor(), dataSetID));
				//

				dm = new XmlDocMaker("args");
				dm.add("id", dataSetID);
				executor().execute("asset.get", dm.root(), null, outputs);
				final PluginService.Output output = outputs.output(0);
				LittleEndianDataInputStream din = new LittleEndianDataInputStream(output.stream());
				try {

					// Parse and get date from the data set
					MRMetaData mm = new MRMetaData(din);
					Date date = mm.getFoRDate();
					String frameOfReference = mm.getFoR();
					String sdate = DateUtil.formatDate(date, "dd-MMM-yyyy");
					if (first) {
						frameOfReferenceAll = frameOfReference;
					} else {
						if (frameOfReference!=frameOfReference) {
							same = false;
						}
					}

					// Set on Study if first DataSet
					if (first && !listOnly) {
						cid = CiteableIdUtil.idToCid(executor(), studyID);
						dm = new XmlDocMaker("args");
						dm.add("id", cid);
						dm.push("meta", new String[]{"action", "merge"});
						dm.push("daris:siemens-raw-mr-study");
						//
						dm.add("date", sdate);
						//
						dm.pop();
						dm.pop();
						executor().execute("om.pssd.study.update", dm.root());
					}
					// List all DataSets
					w.add("date", sdate);
					w.add("for", frameOfReference);


					// Now set on all children data sets ?
				} finally{
					din.close();
				}
				w.pop();
			}
			w.add("for-identical", same);
			w.pop();
			first = false;
		}
	}
}
