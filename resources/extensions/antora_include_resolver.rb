# frozen_string_literal: true
require 'open-uri'
require 'asciidoctor/include_ext/include_processor'

module Neo4j
  # Asciidoctor extensions by Neo4j
  module AsciidoctorExtensions
    include Asciidoctor

    RESOURCE_ID_DETECTOR_RX = /[$:@]/
    EXAMPLES_DIR_TOKEN = 'example$'
    PARTIALS_DIR_TOKEN = 'partial$'

    # Resolve include directives that are using an Antora resource identifier.
    # Only works when the resource is inside the same component and module.
    #
    class AntoraIncludeResolver < Asciidoctor::IncludeExt::IncludeProcessor
      def resolve_target_path(target, reader)
        return target if target_uri? target
        return target unless RESOURCE_ID_DETECTOR_RX =~ target
        return target unless target.start_with?(PARTIALS_DIR_TOKEN) || target.start_with?(EXAMPLES_DIR_TOKEN)

        family, relative = split_once(target, '$')
        relative = relative[1..-1] if relative[0] == '/'

        if family == 'example$'
          "#{reader.dir}/../examples/#{relative}"
        else
          "#{reader.dir}/../partials/#{relative}"
        end
      end

      def read_lines(filename, selector)
        if selector
          open(filename).select.with_index(1, &selector)
        else
          open(filename, &:read)
        end
      end

      private

      def split_once(value, separator)
        separator_idx = value.index(separator)
        separator_idx ? [value[0..separator_idx], value[separator_idx + 1..-1]] : [value]
      end
    end
  end
end

Asciidoctor::Extensions.register do
  include_processor Neo4j::AsciidoctorExtensions::AntoraIncludeResolver.new
end
