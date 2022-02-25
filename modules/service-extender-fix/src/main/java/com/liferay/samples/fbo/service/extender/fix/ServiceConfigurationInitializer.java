package com.liferay.samples.fbo.service.extender.fix;

import com.liferay.portal.kernel.configuration.Configuration;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.security.permission.ResourceActions;
import com.liferay.portal.kernel.service.ServiceComponentLocalService;
import com.liferay.portal.kernel.service.configuration.ServiceComponentConfiguration;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapDictionary;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Properties;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class ServiceConfigurationInitializer {

	public ServiceConfigurationInitializer(
		Bundle bundle, ClassLoader classLoader,
		Configuration portletConfiguration, Configuration serviceConfiguration,
		ResourceActions resourceActions,
		ServiceComponentLocalService serviceComponentLocalService) {

		_bundle = bundle;
		_classLoader = classLoader;
		_portletConfiguration = portletConfiguration;
		_serviceConfiguration = serviceConfiguration;

		_serviceComponentConfiguration = new ModuleResourceLoader(bundle);
		_resourceActions = resourceActions;
		_serviceComponentLocalService = serviceComponentLocalService;
	}

	public void stop() {
		_serviceComponentLocalService.destroyServiceComponent(
			_serviceComponentConfiguration, _classLoader);

		for (ServiceRegistration<?> serviceRegistration :
				_serviceRegistrations) {

			serviceRegistration.unregister();
		}

		_serviceRegistrations.clear();
	}

	protected void start() {
		BundleContext bundleContext = _bundle.getBundleContext();

		if (_portletConfiguration != null) {
			_readResourceActions();

			_registerConfiguration(
				bundleContext, _portletConfiguration, "portlet");
		}

		if (_serviceConfiguration != null) {
			_initServiceComponent();

			_registerConfiguration(
				bundleContext, _serviceConfiguration, "service");
		}
	}

	private void _initServiceComponent() {
		Properties properties = _serviceConfiguration.getProperties();

		if (properties.isEmpty()) {
			return;
		}

		String buildNamespace = GetterUtil.getString(
			properties.getProperty("build.namespace"));
		long buildNumber = GetterUtil.getLong(
			properties.getProperty("build.number"));
		long buildDate = GetterUtil.getLong(
			properties.getProperty("build.date"));

		if (_log.isDebugEnabled()) {
			_log.debug("Build namespace " + buildNamespace);
			_log.debug("Build number " + buildNumber);
			_log.debug("Build date " + buildDate);
		}

		if (Validator.isNull(buildNamespace)) {
			return;
		}

		try {
			_serviceComponentLocalService.initServiceComponent(
				_serviceComponentConfiguration, _classLoader, buildNamespace,
				buildNumber, buildDate);
		}
		catch (PortalException pe) {
			_log.error("Unable to initialize service component", pe);
		}
	}

	private void _readResourceActions() {
		try {
			String portlets = _portletConfiguration.get(
				"service.configurator.portlet.ids");

			if (Validator.isNull(portlets)) {
				_resourceActions.readAndCheck(
					null, _classLoader,
					StringUtil.split(
						_portletConfiguration.get(
							PropsKeys.RESOURCE_ACTIONS_CONFIGS)));
			}
			else {
				_resourceActions.read(
					null, _classLoader,
					StringUtil.split(
						_portletConfiguration.get(
							PropsKeys.RESOURCE_ACTIONS_CONFIGS)));

				for (String portletId : StringUtil.split(portlets)) {
					_resourceActions.check(portletId);
				}
			}
		}
		catch (Exception e) {
			_log.error(
				"Unable to read resource actions config in " +
					PropsKeys.RESOURCE_ACTIONS_CONFIGS,
				e);
		}
	}

	private void _registerConfiguration(
		BundleContext bundleContext, Configuration configuration, String name) {

		Dictionary<String, Object> properties = new HashMapDictionary<>();

		properties.put("name", name);
		properties.put(
			"origin.bundle.symbolic.name", _bundle.getSymbolicName());

		_serviceRegistrations.add(
			bundleContext.registerService(
				Configuration.class, configuration, properties));
	}

	private static final Log _log = LogFactoryUtil.getLog(
		ServiceConfigurationInitializer.class);

	private final Bundle _bundle;
	private final ClassLoader _classLoader;
	private final Configuration _portletConfiguration;
	private final ResourceActions _resourceActions;
	private final ServiceComponentConfiguration _serviceComponentConfiguration;
	private final ServiceComponentLocalService _serviceComponentLocalService;
	private final Configuration _serviceConfiguration;
	private final List<ServiceRegistration<?>> _serviceRegistrations =
		new ArrayList<>();

}