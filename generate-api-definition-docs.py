import json
import argparse
#
# Generates documentation from a JSON schema
# Usage example
# `python generate-api-definition-docs.py app/resources/api-definition-schema.json > docs/api-definition.md`
# Will generate markdown and output to STDOUT
parser = argparse.ArgumentParser(description='Generate documentation from a JSON schema')
parser.add_argument('schema_file', metavar='FILE', help='JSON file containing the JSON schema')
parser.add_argument('-v', '--verbose', action='store_true', help='Enable verbose output')

args = parser.parse_args()


def output(text):
    print(text)


def output_row(name, definition, is_required, **kwargs):
    """
    Output a table row markdown detailing information about JSON key
    :param name: Name of the JSON key
    :param definition: dict, the JSON schema definition for the key
    :param is_required: Is the key Optional or Required
    :param kwargs: name_link=boolean, if True outputs the name as an anchor link
    :return: None
    """
    enum = definition.get('enum')
    type = definition.get('type')
    if type == 'array':
        type = definition.get('items').get('type') + '[]'
        if definition.get('items').get('type') == 'string':
            enum = definition.get('items').get('enum')
    enum = '' if not isinstance(enum, list) else '<br>'.join(enum)
    output(
        '| {} | {} | {} | {} | {}'.format(
            '[{0}](#{0})'.format(name.lower()) if kwargs['name_link'] is True else name,
            type,
            is_required,
            definition.get('description', ''),
            enum
        )
    )


def output_object(name, schema_object):
    """
    Output the markdown for a JSON schema object
    :param name: Name of the object/key
    :param object: JSON schema definition
    :return: None
    """
    required = schema_object.get('required', [])
    children = []

    output('## {}'.format(name))
    output(schema_object.get('description', ''))
    output('')
    output('| {} | {} | {} | {} | {}'.format('key', 'type', 'required', 'description', 'values'))
    output('| --- | --- | --- | --- | --- |')
    for name, definition in schema_object['properties'].items():
        is_required = 'Required' if name in required else 'Optional'
        name_link = False
        if definition.get('type') == 'object':
            children.append({'name': name, 'definition': definition})
            name_link = True
        if definition.get('type') == 'array' and definition.get('items').get('type') == 'object':
            children.append({'name': name, 'definition': definition.get('items')})
            name_link = True
        output_row(name, definition, is_required, name_link=name_link)

    for child in children:
        output_object(child['name'], child['definition'])


with open(args.schema_file) as file:
    schema = json.load(file)

output_object('Root', schema)
