# frozen_string_literal: true

require 'asciidoctor/extensions' unless RUBY_ENGINE == 'opal'

module Neo4j
  # Asciidoctor extensions by Neo4j
  module AsciidoctorExtensions
    include Asciidoctor

    # Manage instructor notes used in a classroom (Google Slides).
    # Please note that, instructor notes blocks will be ignored/removed on other backends.
    #
    class InstructorNotesTreeProcessor < Extensions::TreeProcessor
      use_dsl

      def process(document)
        document.find_by(context: :section).each do |section|
          notes_blocks = section.blocks.select { |block| block.context == :open && block.roles.include?('notes') }
          instructor_notes_blocks = section.blocks.select { |block| block.context == :open && block.roles.include?('instructor-notes') }
          next if instructor_notes_blocks.empty?

          if document.backend == 'googleslides'
            # replace notes blocks when there's at least one instructor-notes block in the section.
            notes_blocks.each do |notes_block|
              section.blocks.delete(notes_block)
            end
            instructor_notes_blocks.each do |instructor_notes_block|
              instructor_notes_block.remove_role('instructor-notes')
              instructor_notes_block.add_role('notes')
            end
          else
            # remove instructor notes (only used in the Google Slides backend)
            instructor_notes_blocks.each do |instructor_notes_block|
              section.blocks.delete(instructor_notes_block)
            end
          end
        end
        document
      end
    end
  end
end

Asciidoctor::Extensions.register do
  tree_processor Neo4j::AsciidoctorExtensions::InstructorNotesTreeProcessor
end
