package com.liferay.samples.fbo.service.extender.fix;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.service.configuration.ServiceComponentConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.osgi.framework.Bundle;

public class ModuleResourceLoader implements ServiceComponentConfiguration {

	public ModuleResourceLoader(Bundle bundle) {
		_bundle = bundle;
	}

	@Override
	public InputStream getHibernateInputStream() {
		return getInputStream("/META-INF/module-hbm.xml");
	}

	@Override
	public InputStream getModelHintsExtInputStream() {
		return getInputStream("/META-INF/portlet-model-hints-ext.xml");
	}

	@Override
	public InputStream getModelHintsInputStream() {
		return getInputStream("/META-INF/portlet-model-hints.xml");
	}

	@Override
	public String getServletContextName() {
		return _bundle.getSymbolicName();
	}

	@Override
	public InputStream getSQLIndexesInputStream() {
		return getInputStream("/META-INF/sql/indexes.sql");
	}

	@Override
	public InputStream getSQLSequencesInputStream() {
		return getInputStream("/META-INF/sql/sequences.sql");
	}

	@Override
	public InputStream getSQLTablesInputStream() {
		return getInputStream("/META-INF/sql/tables.sql");
	}

	protected InputStream getInputStream(String location) {
		URL url = _bundle.getResource(location);

		if (url == null) {
			if (_log.isDebugEnabled()) {
				_log.debug("Unable to find " + location);
			}

			return null;
		}

		InputStream inputStream = null;

		try {
			inputStream = url.openStream();
		}
		catch (IOException ioe) {
			_log.error("Unable to read " + location, ioe);
		}

		return inputStream;
	}

	private static final Log _log = LogFactoryUtil.getLog(
		ModuleResourceLoader.class);

	private final Bundle _bundle;

}