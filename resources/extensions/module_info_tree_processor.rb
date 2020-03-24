require 'asciidoctor/extensions' unless RUBY_ENGINE == 'opal'
require 'pathname'

include Asciidoctor

class ModuleInfoTreeProcessor < Extensions::TreeProcessor; use_dsl

  TESTING_SLUG_PREFIX = '_testing_'

  def process(document)
    if (docdir = document.attr 'docdir')
      path = File.expand_path('../build/online/asciidoctor-module-descriptor.yml', docdir)
      if File.exist?(path)
        require 'yaml'
        module_descriptor = YAML::load_file(path)
        if (document_slug = document.attr 'slug')
          if (document.attr 'stage') != 'production'
            document_slug = "#{TESTING_SLUG_PREFIX}#{document_slug}"
            document.set_attr 'slug', document_slug
          end
        end
        module_descriptor['pages'].each_with_index do |page, index|
          document.set_attribute "module-toc-link-#{index}", page['url']
          document.set_attribute "module-toc-title-#{index}", page['title']
          page_slug = page['slug']
          if (document.attr 'stage') != 'production'
            page_slug = "#{TESTING_SLUG_PREFIX}#{page_slug}"
          end
          document.set_attribute "module-toc-slug-#{index}", page_slug
          document.set_attribute "module-quiz-#{index}", page['quiz']
          if document_slug == page_slug
            if page.has_key?('next')
              next_page_slug = page['next']['slug']
              if (document.attr 'stage') != 'production'
                next_page_slug = "#{TESTING_SLUG_PREFIX}#{next_page_slug}"
              end
              document.set_attr 'module-next-slug', next_page_slug, false
              document.set_attr 'module-next-title', page['next']['title'], false
            end
            document.set_attribute 'module-quiz', page['quiz']
            document.set_attribute 'module-certificate', page['certificate']
          end
        end
        document.set_attribute 'module-name', module_descriptor['module_name']
      end
    end
    document
  end
end

Extensions.register do
  tree_processor ModuleInfoTreeProcessor
end
