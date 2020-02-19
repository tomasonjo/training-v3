require 'asciidoctor/extensions' unless RUBY_ENGINE == 'opal'
require 'pathname'

include Asciidoctor

class ModuleTableOfContentsTreeProcessor < Extensions::TreeProcessor; use_dsl
  def process(document)
    if (docdir = document.attr 'docdir')
      path = File.expand_path('../build/online/asciidoctor_table_of_contents.rb', docdir)
      if File.exist?(path)
        require path
        ASCIIDOCTOR_TABLE_OF_CONTENTS.each_with_index do |(key, value), index|
          document.set_attribute "module_toc_link_#{index}", key
          document.set_attribute "module_toc_title_#{index}", value
        end
      end
    end
    document
  end
end

Extensions.register do
  tree_processor ModuleTableOfContentsTreeProcessor
end
