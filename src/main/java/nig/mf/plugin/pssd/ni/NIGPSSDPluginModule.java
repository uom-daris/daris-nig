package nig.mf.plugin.pssd.ni;

import java.util.Collection;
import java.util.Vector;

import arc.mf.plugin.ConfigurationResolver;
import arc.mf.plugin.PluginModule;
import arc.mf.plugin.PluginService;

public class NIGPSSDPluginModule implements PluginModule {

	private Collection<PluginService> _svs = null;

	@Override
	public String description() {

		return "NIG PSSD Plugins Module for MediaFlux";
	}

	@Override
	public void initialize(ConfigurationResolver config) throws Throwable {

		_svs = new Vector<PluginService>();
		_svs.add(new SvcMetadataGet());
		_svs.add(new SvcProjectTimePointCheck());
		_svs.add(new SvcPSSDIdentityGrab());
		_svs.add(new SvcProjectMigrate());
		_svs.add(new SvcProjectNameCheck());
		_svs.add(new SvcSubjectMetaSet());
		_svs.add(new SvcBase64());
		//
		_svs.add(new SvcUserCreate());
		_svs.add(new SvcDICOMUserCreate());
		//
		_svs.add(new SvcProjectMetaDataHarvest());

		/*
		 * Specialised one off services
		 */
		// _svs.add(new SvcRSubjectPathologyMigrate());
		// _svs.add(new SvcSubjectPathologyMigrate());
		_svs.add(new SvcDataSetMetaCopy());
		// _svs.add(new SvcPSSDBrukerDataSetTimeFix());

		// MBC
		// _svs.add(new SvcMBCProjectImport()); // No longer using DICOM data
		// model
		// _svs.add(new SvcMBCProjectSet()); // No longer using DICOM Data model
		// archive
		// _svs.add(new SvcMBCSeriesSourceSet()); // One shot
		//
		_svs.add(new SvcMBCMRStudyStationNameSet());
		//
		_svs.add(new SvcMBCPETVarCheck());
		_svs.add(new SvcMBCDoseUpload());
		_svs.add(new SvcMBCFMPUploads());
		//
		// One off processw completed
		//_svs.add(new SvcMBCHumanProjectMigrate());
		//_svs.add(new SvcMBCNonHumanProjectMigrate());
		//_svs.add(new SvcMBCEndUserProjectMigrate());
		//
		_svs.add(new SvcMBCStudyDICOMMetaGet());
		_svs.add(new SvcMBCMRStudyRawMetaGet());
		_svs.add(new SvcMBCMRDataSetRawMetaGet());
		_svs.add(new SvcMBCVisitList());
		_svs.add(new SvcMBCPETHasRaw());

		// Test service
		_svs.add(new SvcTesting());
	
	}

	@Override
	public void shutdown(ConfigurationResolver config) throws Throwable {

	}

	@Override
	public String vendor() {

		return "Neuroimaging and Neuroinformatics Group, Department of Anatomy and Neuroscience, the University of Melbourne.";
	}

	@Override
	public String version() {

		return "1.1";
	}

	public Collection<PluginService> services() {

		return _svs;
	}

}