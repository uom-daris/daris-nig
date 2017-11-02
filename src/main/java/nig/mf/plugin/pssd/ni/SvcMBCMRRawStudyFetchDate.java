package nig.mf.plugin.pssd.ni;

import java.util.Collection;
import java.util.Date;

import nig.io.LittleEndianDataInputStream;
import nig.mf.plugin.pssd.util.ni.MRMetaData;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import nig.util.DateUtil;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.PluginService.Interface.Element;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;


public class SvcMBCMRRawStudyFetchDate extends PluginService {

	private Interface _defn;

	public SvcMBCMRRawStudyFetchDate() {
		_defn = new Interface();
		_defn.add(new Element("id", CiteableIdType.DEFAULT, "The citeable asset id of the parent object.", 1, 1));
	}

	public String name() {

		return "nig.pssd.mbic.mr.raw.study.fetch.date";
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
		String pid = args.stringValue("id");
		//
		// Find raw studies
		XmlDocMaker dm = new XmlDocMaker("args");
		String where = "(cid='" + pid +"' or cid starts with '" + pid + 
				"') and model='om.pssd.study' and daris:siemens-raw-mr-study has value and xpath(daris:siemens-raw-mr-study/date) hasno value";
		dm.add("where", where);
		dm.add("size", "infinity");
		dm.add("pdist", "0");
		XmlDoc.Element r = executor().execute("asset.query", dm.root());
		if (r==null) return;
		//
		Collection<String> studyIDs = r.values("id");
		if (studyIDs==null) return;
		for (String studyID : studyIDs) {
			String studyCID = CiteableIdUtil.idToCid(executor(), studyID);
			w.add("study", studyCID);
			// For the STudy, find the first raw data file
			dm = new XmlDocMaker("args");
			where = "cid starts with '" + studyCID + "' and model='om.pssd.dataset' and type='siemens-raw-mr/series'";
			dm.add("where", where);
			dm.add("size", "1");
			dm.add("pdist", "0");
			r = executor().execute("asset.query", dm.root());

			// Fetch the first data set
			if (r!=null) {
				PluginService.Outputs outputs = new PluginService.Outputs(1);
				String dataSetID = r.value("id");
				w.push("data-set");
				if (dataSetID!=null) {
					w.add("id", CiteableIdUtil.idToCid(executor(), dataSetID));
					dm = new XmlDocMaker("args");
					dm.add("id", dataSetID);
					executor().execute("asset.get", dm.root(), null, outputs);
					final PluginService.Output output = outputs.output(0);
					LittleEndianDataInputStream din = new LittleEndianDataInputStream(output.stream());
					try {

						// Parse and get date from the data set
						MRMetaData mm = new MRMetaData(din);
						Date date = mm.getFoRDate();

						// Set on Study
						String cid = CiteableIdUtil.idToCid(executor(), studyID);
						dm = new XmlDocMaker("args");
						dm.add("id", cid);
						dm.push("meta", new String[]{"action", "merge"});
						dm.push("daris:siemens-raw-mr-study");
						String sdate = DateUtil.formatDate(date, "dd-MMM-yyyy");
						dm.add("date", sdate);
						dm.pop();
						dm.pop();
						executor().execute("om.pssd.study.update", dm.root());

						// Now set on all children data sets
					} finally{
						din.close();
					}
				} else {
					w.add("id", "none");
				}
				w.pop();
			}

		}
	}
}
