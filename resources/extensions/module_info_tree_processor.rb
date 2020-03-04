require 'asciidoctor/extensions' unless RUBY_ENGINE == 'opal'
require 'pathname'

include Asciidoctor

class ModuleInfoTreeProcessor < Extensions::TreeProcessor; use_dsl
  def process(document)
    if (docdir = document.attr 'docdir')
      path = File.expand_path('../build/online/asciidoctor-module-descriptor.yml', docdir)
      if File.exist?(path)
        require 'yaml'
        module_descriptor = YAML::load_file(path)
        document_slug = (document.attr 'slug')
        module_descriptor['nav'].each_with_index do |item, index|
          document.set_attribute "module-toc-link-#{index}", item['url']
          document.set_attribute "module-toc-title-#{index}", item['title']
          document.set_attribute "module-toc-slug-#{index}", item['slug']
          if item.has_key?('next') && (document_slug == item['slug'])
            document.set_attr "module-next-slug", item['next']['slug'], false
            document.set_attr "module-next-title", item['next']['title'], false
          end
        end
        document.set_attribute "module-name", module_descriptor['module_name']
      end
    end
    document
  end
end

Extensions.register do
  tree_processor ModuleInfoTreeProcessor
end
