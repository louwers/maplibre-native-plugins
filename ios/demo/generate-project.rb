# Generates the iOS demo app project for a plugin. Run by tools/bin/plugin run-ios.
# Usage: ruby generate-project.rb DEMO_DIRECTORY  (settings come from MLN_DEMO_* variables)
require 'xcodeproj'

demo_dir = File.expand_path(ARGV.fetch(0))
plugin_root = ENV.fetch('MLN_DEMO_PLUGIN_ROOT')
product_name = ENV.fetch('MLN_DEMO_PRODUCT')
bundle_id = ENV.fetch('MLN_DEMO_BUNDLE_ID')
deployment_target = ENV.fetch('MLN_DEMO_DEPLOYMENT_TARGET')
display_name = ENV.fetch('MLN_DEMO_DISPLAY_NAME')
maplibre_url = ENV.fetch('MLN_DEMO_MAPLIBRE_PACKAGE')
maplibre_version = ENV.fetch('MLN_DEMO_MAPLIBRE_VERSION')
maplibre_path = ENV['MAPLIBRE_IOS_PATH']

Dir.chdir(demo_dir)
project = Xcodeproj::Project.new('PluginDemo.xcodeproj')
app = project.new_target(:application, 'PluginDemo', :ios, deployment_target)

group = project.main_group.new_group('App', 'App')
app.add_file_references([group.new_file('main.m')])
# A folder reference keeps the demo style next to any files it references relatively.
demo_folder = project.main_group.new_reference('Demo')
demo_folder.last_known_file_type = 'folder'
app.resources_build_phase.add_file_reference(demo_folder)

def add_package_product(project, app, package, product_name)
  product = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
  product.package = package unless package.is_a?(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
  product.product_name = product_name
  app.package_product_dependencies << product
  build_file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
  build_file.product_ref = product
  app.frameworks_build_phase.files << build_file
end

plugin_package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
plugin_package.relative_path = Pathname.new(plugin_root).relative_path_from(Pathname.new(demo_dir)).to_s
project.root_object.package_references << plugin_package
add_package_product(project, app, plugin_package, product_name)

# The application links the plugin-enabled MapLibre framework, the same package and version
# the plugin compiles against.
if maplibre_path
  maplibre_package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
  maplibre_package.relative_path = maplibre_path
else
  maplibre_package = project.new(Xcodeproj::Project::Object::XCRemoteSwiftPackageReference)
  maplibre_package.repositoryURL = maplibre_url
  maplibre_package.requirement = { 'kind' => 'exactVersion', 'version' => maplibre_version }
end
project.root_object.package_references << maplibre_package
add_package_product(project, app, maplibre_package, 'MapLibre')

# Use the selected SDK instead of the generator gem's default SDK version.
project.files.select { |file| file.path&.end_with?('Foundation.framework') }.each do |file|
  file.path = 'System/Library/Frameworks/Foundation.framework'
  file.source_tree = 'SDKROOT'
end

app.build_configurations.each do |config|
  settings = config.build_settings
  settings['GENERATE_INFOPLIST_FILE'] = 'YES'
  settings['INFOPLIST_KEY_CFBundleDisplayName'] = display_name
  settings['INFOPLIST_KEY_UILaunchScreen_Generation'] = 'YES'
  settings['INFOPLIST_KEY_UISupportedInterfaceOrientations'] =
    'UIInterfaceOrientationPortrait UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight'
  settings['CLANG_ENABLE_MODULES'] = 'YES'
  settings['CLANG_ENABLE_OBJC_ARC'] = 'YES'
  # MapLibre's framework headers use quoted includes.
  settings['CLANG_WARN_QUOTED_INCLUDE_IN_FRAMEWORK_HEADER'] = 'NO'
  settings['IPHONEOS_DEPLOYMENT_TARGET'] = deployment_target
  settings['TARGETED_DEVICE_FAMILY'] = '1,2'
  settings['SUPPORTS_MACCATALYST'] = 'NO'
  settings['PRODUCT_BUNDLE_IDENTIFIER'] = bundle_id
  settings['CODE_SIGN_STYLE'] = 'Automatic'
  settings['LD_RUNPATH_SEARCH_PATHS'] = ['$(inherited)', '@executable_path/Frameworks']
  settings['MARKETING_VERSION'] = '1.0'
  settings['CURRENT_PROJECT_VERSION'] = '1'
end
project.save

scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(app)
scheme.set_launch_target(app)
scheme.save_as(project.path, 'PluginDemo', true)
