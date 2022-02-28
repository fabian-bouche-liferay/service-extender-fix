package com.liferay.samples.fbo.service.extender.fix;

import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.configuration.Configuration;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Release;
import com.liferay.portal.kernel.module.framework.ModuleServiceLifecycle;
import com.liferay.portal.kernel.security.permission.ResourceActions;
import com.liferay.portal.kernel.service.ServiceComponentLocalService;

import java.util.Collection;
import java.util.Dictionary;

import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.ServiceDependency;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;

@Component(
		immediate = true,
		service = ServiceExtenderFix.class
		)
public class ServiceExtenderFix {

	@Activate
	public void activate(BundleContext context) {

		Bundle[] bundles = context.getBundles();
		for(int i = 0; i < bundles.length; i++) {
			Bundle bundle = bundles[i];
			fixBundle(bundle);
		}
		
	}
	
	protected void fixBundle(Bundle bundle) {
		
		Dictionary<String, String> headers = bundle.getHeaders(StringPool.BLANK);

		if (headers.get("Liferay-Service") == null) {
			_log.debug("Bundle " + bundle.getSymbolicName() + " is not a Liferay-Service");
			return;
		}
		
		try {
			Collection<ServiceReference<Configuration>> serviceReferences = 
					bundle.getBundleContext().getServiceReferences(
							Configuration.class,
							"(&(origin.bundle.symbolic.name=" + bundle.getSymbolicName() + "))"
					);
			if(serviceReferences.size() != 0) {
				_log.debug("Bundle " + bundle.getSymbolicName() + " has already registered a service configuration extender");
				return;
			}
			
		} catch (InvalidSyntaxException e) {
			_log.error("Invalid filter syntax", e);
		}
	
		_log.error("Registering a service configuration extender into " + bundle.getSymbolicName());
		
		BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);

		ClassLoader classLoader = bundleWiring.getClassLoader();

		Configuration portletConfiguration = ConfigurationUtil.getConfiguration(
			classLoader, "portlet");
		Configuration serviceConfiguration = ConfigurationUtil.getConfiguration(
			classLoader, "service");

		if ((portletConfiguration == null) && (serviceConfiguration == null)) {
			_log.error("Neither portlet nor service configuration");
			return;
		}
		
		String requireSchemaVersion = headers.get(
				"Liferay-Require-SchemaVersion");

		ServiceConfigurationInitializer serviceConfigurationInitializer =
			new ServiceConfigurationInitializer(
				bundle, classLoader, portletConfiguration, serviceConfiguration,
				_resourceActions, _serviceComponentLocalService);

		ServiceConfigurationExtension serviceConfigurationExtension =
			new ServiceConfigurationExtension(
				bundle, requireSchemaVersion, serviceConfigurationInitializer);

		serviceConfigurationExtension.start();
		
	}
	
	public static class ServiceConfigurationExtension {

		public void destroy() {
			_dependencyManager.remove(_component);
		}

		public void start() {
			_dependencyManager.add(_component);
		}

		private ServiceConfigurationExtension(
			Bundle bundle, String requireSchemaVersion,
			ServiceConfigurationInitializer serviceConfigurationInitializer) {

			_dependencyManager = new DependencyManager(
				bundle.getBundleContext());

			_component = _dependencyManager.createComponent();

			_component.setImplementation(serviceConfigurationInitializer);

			if (requireSchemaVersion == null) {
				return;
			}

			String versionRangeFilter = null;

			// See LPS-76926

			try {
				Version version = new Version(requireSchemaVersion);

				versionRangeFilter = _getVersionRangerFilter(version);
			}
			catch (IllegalArgumentException iae) {
				try {
					VersionRange versionRange = new VersionRange(
						requireSchemaVersion);

					versionRangeFilter = versionRange.toFilterString(
						"release.schema.version");
				}
				catch (IllegalArgumentException iae2) {
					iae.addSuppressed(iae2);

					if (_log.isWarnEnabled()) {
						_log.warn(
							"Invalid \"Liferay-Require-SchemaVersion\" " +
								"header for bundle: " + bundle.getBundleId(),
							iae);
					}
				}
			}

			if (versionRangeFilter == null) {
				return;
			}

			ServiceDependency serviceDependency =
				_dependencyManager.createServiceDependency();

			serviceDependency.setRequired(true);

			serviceDependency.setService(
				Release.class,
				StringBundler.concat(
					"(&(release.bundle.symbolic.name=",
					bundle.getSymbolicName(), ")", versionRangeFilter,
					"(|(!(release.state=*))(release.state=0)))"));

			_log.error("Component was created, adding it to the registry...");

			_component.add(serviceDependency);

			_log.error("Done!");

		}

		private String _getVersionRangerFilter(Version version) {
			StringBundler sb = new StringBundler(7);

			sb.append("(&(release.schema.version>=");
			sb.append(version.getMajor());
			sb.append(".");
			sb.append(version.getMinor());
			sb.append(".0)(!(release.schema.version>=");
			sb.append(version.getMajor() + 1);
			sb.append(".0.0)))");

			return sb.toString();
		}

		private final org.apache.felix.dm.Component _component;
		private final DependencyManager _dependencyManager;

	}

	private static final Log _log = LogFactoryUtil.getLog(
			ServiceExtenderFix.class);

	@Reference(target = ModuleServiceLifecycle.PORTAL_INITIALIZED)
	private ModuleServiceLifecycle _moduleServiceLifecycle;

	@Reference
	private ResourceActions _resourceActions;

	@Reference
	private ServiceComponentLocalService _serviceComponentLocalService;
	
	@Reference
	private ServiceComponentRuntime _serviceComponentRuntime;

}
