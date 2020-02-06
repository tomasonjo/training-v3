require 'asciidoctor/extensions' unless RUBY_ENGINE == 'opal'

include Asciidoctor

class RevealJsSpeakerNotesAggregatorTreeProcessor < Extensions::TreeProcessor; use_dsl
  def process(document)
    if document.backend == 'revealjs'
      document.find_by({context: :section}).each do |section|
        notes_blocks = section.blocks.select { |block| block.context == :open && block.roles.include?('notes') }
        next if notes_blocks.empty?
        agg_notes_block = Block.new(section, :open, {attributes: {role: 'notes'}})
        notes_blocks.each do |notes_block|
          section.blocks.delete(notes_block)
          agg_notes_block << notes_block
        end
        section.blocks << agg_notes_block
      end
    end
    document
  end
end

Extensions.register do
  tree_processor RevealJsSpeakerNotesAggregatorTreeProcessor
end
