require 'asciidoctor/extensions' unless RUBY_ENGINE == 'opal'

include Asciidoctor

class DocumentInfoPostProcessor < Extensions::Postprocessor; use_dsl
  def process(document, _)
    if document.backend == 'html5'
      if (outfile = document.attr 'outfile')
        require 'yaml'
        info = {}
        tags = []
        if document.attr? 'tags'
          tags = (document.attr 'tags').split(',').map { |tag| tag.strip! }.reject { |tag| tag.empty? }
        end
        tags << 'public' if document.attr? 'public'
        tags << 'private' if document.attr? 'private'
        unless tags.empty?
          info['tags'] = tags
        end
        if (slug = document.attr 'slug')
          info['slug'] = slug
        end
        info['title'] = document.doctitle
        outputdir = File.dirname(outfile)
        filename = File.basename(outfile, File.extname(outfile))
        File.open("#{outputdir}/#{filename}.yml", "w") { |file| file.write(info.to_yaml) }
      end
    end
    document
  end
end

Extensions.register do
  postprocessor DocumentInfoPostProcessor
end
