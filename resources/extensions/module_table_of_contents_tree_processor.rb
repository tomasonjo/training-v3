require 'asciidoctor/extensions' unless RUBY_ENGINE == 'opal'
require 'pathname'

include Asciidoctor

class ModuleTableOfContentsTreeProcessor < Extensions::TreeProcessor; use_dsl
  def process(document)
    if (docdir = document.attr 'docdir')
      path = File.expand_path('../build/online/asciidoctor-module-descriptor.yml', docdir)
      if File.exist?(path)
        require 'yaml'
        module_descriptor = YAML::load_file(path)
        p module_descriptor
        module_descriptor['nav'].each_with_index do |item, index|
          document.set_attribute "module_toc_link_#{index}", item['url']
          document.set_attribute "module_toc_title_#{index}", item['title']
          document.set_attribute "module_toc_slug_#{index}", item['slug']
        end
      end
    end
    document
  end
end

Extensions.register do
  tree_processor ModuleTableOfContentsTreeProcessor
end
