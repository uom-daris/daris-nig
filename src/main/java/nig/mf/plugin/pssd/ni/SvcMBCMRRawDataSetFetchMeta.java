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
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;


public class SvcMBCMRRawDataSetFetchMeta extends PluginService {

	private Interface _defn;

	public SvcMBCMRRawDataSetFetchMeta() {
		_defn = new Interface();
		_defn.add(new Element("id", CiteableIdType.DEFAULT, "The citeable asset id of the parent object.", 1, 1));
	}

	public String name() {

		return "nig.pssd.mbic.mr.raw.dataset.fetch.meta";
	}

	public String description() {

		return "For all raw Siemens MR data sets  without the daris:siemens-raw-mr-series meta-data, parse the data set header and repopulate it.";
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
		String where = "(cid='" + pid +"' or cid starts with '" + pid + "') and type='siemens-raw-mr/series' " +
				" and model='om.pssd.dataset' and daris:siemens-raw-mr-series hasno value";
		dm.add("where", where);
		dm.add("size", "infinity");
		dm.add("pdist", "0");
		XmlDoc.Element r = executor().execute("asset.query", dm.root());
		if (r==null) return;
		//
		Collection<String> dataSetIDs = r.values("id");
		if (dataSetIDs==null) return;
		for (String dataSetID : dataSetIDs) {
			PluginTask.checkIfThreadTaskAborted();

			// Fetch the data set content
			String dataSetCID = CiteableIdUtil.idToCid(executor(), dataSetID);
			w.add("dataset", dataSetCID);
			//
			PluginService.Outputs outputs = new PluginService.Outputs(1);
			dm = new XmlDocMaker("args");
			dm.add("id", dataSetID);
			r = executor().execute("asset.get", dm.root(), null, outputs);
			String ctime = r.value("asset/ctime");
			
			// Parse the file
			final PluginService.Output output = outputs.output(0);
			LittleEndianDataInputStream din = new LittleEndianDataInputStream(output.stream());
			try {
				MRMetaData mm = new MRMetaData(din);
				setMetaData (executor(), mm, dataSetCID,  ctime);
			} finally{
				din.close();
			}
		}
	}
	
	private void setMetaData (ServiceExecutor executor, MRMetaData mm,
			String dataSetCID, String ctime) throws Throwable {

		// Create a study with siemens doc attached
		XmlDocMaker w = new XmlDocMaker("args");
		w.add("id", dataSetCID);

		w.push("meta");
		w.push("daris:siemens-raw-mr-series");
		String date = DateUtil.formatDate(mm.getFoRDate(), false, false);
		w.add("date", date);
		w.add("modality", "MR");
		w.add("description", "Siemens RAW MR file");
		
		// We will get this from the CTIME
		w.push("ingest");
		w.add("date", ctime);
		w.pop();
		//
		w.pop();
		w.pop();	
	    executor.execute("om.pssd.dataset.primary.update", w.root());
	}


}
