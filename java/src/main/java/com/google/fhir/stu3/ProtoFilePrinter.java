//    Copyright 2018 Google Inc.
//
//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//
//        https://www.apache.org/licenses/LICENSE-2.0
//
//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.

package com.google.fhir.stu3;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.fhir.stu3.proto.Annotations;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.DescriptorProtos.OneofOptions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A utility to turn protocol message descriptors into .proto files. */
public class ProtoFilePrinter {

  private static final String LICENSE =
      "//    Copyright 2018 Google Inc.\n"
          + "//\n"
          + "//    Licensed under the Apache License, Version 2.0 (the \"License\");\n"
          + "//    you may not use this file except in compliance with the License.\n"
          + "//    You may obtain a copy of the License at\n"
          + "//\n"
          + "//        https://www.apache.org/licenses/LICENSE-2.0\n"
          + "//\n"
          + "//    Unless required by applicable law or agreed to in writing, software\n"
          + "//    distributed under the License is distributed on an \"AS IS\" BASIS,\n"
          + "//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
          + "//    See the License for the specific language governing permissions and\n"
          + "//    limitations under the License.\n";

  private boolean addLicense = false;

  /** Creates a ProtoFilePrinter with default parameters. */
  public ProtoFilePrinter() {
    this(false);
  }

  private ProtoFilePrinter(boolean addLicense) {
    this.addLicense = addLicense;
  }

  /** Returns a ProtoFilePrinter which will add an Apache 2.0 license to any output. */
  public ProtoFilePrinter withApacheLicense() {
    return new ProtoFilePrinter(true);
  }

  /** Generate a .proto file corresponding to the provided FileDescriptorProto. */
  public String print(FileDescriptorProto fileDescriptor) {
    String fullyQualifiedPackageName = "." + fileDescriptor.getPackage();
    StringBuilder contents = new StringBuilder();
    if (addLicense) {
      contents.append(LICENSE).append("\n");
    }
    contents.append(printHeader(fileDescriptor)).append("\n");
    contents.append(printImports(fileDescriptor)).append("\n");
    contents.append(printOptions(fileDescriptor)).append("\n");
    for (DescriptorProto descriptor : fileDescriptor.getMessageTypeList()) {
      contents
          .append(printMessage(descriptor, fullyQualifiedPackageName, fullyQualifiedPackageName))
          .append("\n");
    }
    return contents.toString();
  }

  private String printHeader(FileDescriptorProto fileDescriptor) {
    StringBuilder header = new StringBuilder();
    if (fileDescriptor.hasSyntax()) {
      header.append("syntax = \"").append(fileDescriptor.getSyntax()).append("\";\n\n");
    }
    if (fileDescriptor.hasPackage()) {
      header.append("package ").append(fileDescriptor.getPackage()).append(";\n");
    }
    return header.toString();
  }

  private String printImports(FileDescriptorProto fileDescriptor) {
    StringBuilder imports = new StringBuilder();
    for (String dependency : fileDescriptor.getDependencyList()) {
      imports.append("import \"").append(dependency).append("\";\n");
    }
    return imports.toString();
  }

  private String printOptions(FileDescriptorProto fileDescriptor) {
    StringBuilder options = new StringBuilder();
    FileOptions fileOptions = fileDescriptor.getOptions();
    if (fileOptions.hasJavaMultipleFiles()) {
      options
          .append("option java_multiple_files = ")
          .append(fileOptions.getJavaMultipleFiles())
          .append(";\n");
    }
    if (fileOptions.hasJavaPackage()) {
      options
          .append("option java_package = \"")
          .append(fileOptions.getJavaPackage())
          .append("\";\n");
    }
    return options.toString();
  }

  private String printMessage(DescriptorProto descriptor, String typePrefix, String packageName) {
    // Get the name of this message.
    String messageName = descriptor.getName();
    String fullName = typePrefix + "." + messageName;
    MessageOptions options = descriptor.getOptions();

    CharMatcher matcher = CharMatcher.is('.');
    String indent =
        Strings.repeat("  ", matcher.countIn(typePrefix) - matcher.countIn(packageName));
    StringBuilder message = new StringBuilder();

    if (options.hasExtension(Annotations.messageDescription)) {
      // Add the main documentation.
      message
          .append(indent)
          .append("// ")
          .append(
              options
                  .getExtension(Annotations.messageDescription)
                  .replaceAll("[\\n\\r]", "\n" + indent + "// "))
          .append("\n");
    }

    // Add a link to the main fhir documentation page for top-level messages.
    if (indent.isEmpty()) {
      if (AnnotationUtils.isPrimitiveType(descriptor)) {
        message
            .append("// See https://www.hl7.org/fhir/datatypes.html#")
            .append(messageName.toLowerCase())
            .append("\n");
      } else if (AnnotationUtils.isResource(descriptor)) {
        message
            .append("// See https://www.hl7.org/fhir/")
            .append(messageName.toLowerCase())
            .append(".html\n");
      }
    }

    // Start the main message.
    message.append(indent).append("message ").append(messageName).append(" {\n");

    String fieldIndent = indent + "  ";
    boolean printedField = false;

    // Add options.
    if (options.hasExtension(Annotations.structureDefinitionKind)) {
      message
          .append(fieldIndent)
          .append("option (structure_definition_kind) = ")
          .append(options.getExtension(Annotations.structureDefinitionKind))
          .append(";\n");
      printedField = true;
    }
    if (options.hasExtension(Annotations.fhirExtensionUrl)) {
      message
          .append(fieldIndent)
          .append("option (fhir_extension_url) = \"")
          .append(options.getExtension(Annotations.fhirExtensionUrl))
          .append("\";\n");
      printedField = true;
    }

    // Loop over the elements.
    Set<String> printedNestedTypeDefinitions = new HashSet<>();
    for (FieldDescriptorProto field : descriptor.getFieldList()) {
      if (!field.hasOneofIndex()) {
        // Keep a newline between fields.
        if (printedField) {
          message.append("\n");
        }

        if (field.getOptions().hasExtension(Annotations.fieldDescription)) {
          // Add a comment describing the field.
          String description = field.getOptions().getExtension(Annotations.fieldDescription);
          message
              .append(fieldIndent)
              .append("// ")
              .append(description.replaceAll("[\\n\\r]", "\n" + indent + "// "))
              .append("\n");
        }

        // Add nested types if necessary.
        message.append(
            maybePrintNestedType(
                descriptor, field, typePrefix, packageName, printedNestedTypeDefinitions));
        message.append(printField(field, fullName, fieldIndent));
        printedField = true;
      }
    }

    // Loop over the oneofs
    String oneofIndent = fieldIndent + "  ";
    for (int oneofIndex = 0; oneofIndex < descriptor.getOneofDeclCount(); oneofIndex++) {
      message
          .append(fieldIndent)
          .append("oneof ")
          .append(descriptor.getOneofDecl(oneofIndex).getName())
          .append(" {\n");
      OneofOptions oneofOptions = descriptor.getOneofDecl(oneofIndex).getOptions();
      if (oneofOptions.hasExtension(Annotations.oneofValidationRequirement)) {
        message
            .append(oneofIndent)
            .append("option (oneof_validation_requirement) = ")
            .append(oneofOptions.getExtension(Annotations.oneofValidationRequirement).toString())
            .append(";\n");
      }
      // Loop over the elements.
      for (FieldDescriptorProto field : descriptor.getFieldList()) {
        if (field.getOneofIndex() == oneofIndex) {
          message.append(printField(field, fullName, oneofIndent));
        }
      }
      message.append(fieldIndent).append("}\n");
    }

    // Close the main message.
    message.append(indent).append("}\n");

    return message.toString();
  }

  private String printEnum(EnumDescriptorProto descriptor, String typePrefix, String packageName) {
    // Get the name of this message.
    String messageName = descriptor.getName();

    CharMatcher matcher = CharMatcher.is('.');
    String indent =
        Strings.repeat("  ", matcher.countIn(typePrefix) - matcher.countIn(packageName));
    StringBuilder message = new StringBuilder();

    // Start the enum.
    message.append(indent).append("enum ").append(messageName).append(" {\n");

    String fieldIndent = indent + "  ";

    // Loop over the elements.
    for (EnumValueDescriptorProto field : descriptor.getValueList()) {
      message
          .append(fieldIndent)
          .append(field.getName())
          .append(" = ")
          .append(field.getNumber())
          .append(";\n");
    }

    // Close the enum.
    message.append(indent).append("}\n");

    return message.toString();
  }

  private String maybePrintNestedType(
      DescriptorProto descriptor,
      FieldDescriptorProto field,
      String typePrefix,
      String packageName,
      Set<String> alreadyPrinted) {
    String prefix = typePrefix + "." + descriptor.getName();
    if (field.hasTypeName()
        && field.getTypeName().startsWith(prefix + ".")
        && !alreadyPrinted.contains(field.getTypeName())) {
      List<String> typeNameParts = Splitter.on('.').splitToList(field.getTypeName());
      String typeName = typeNameParts.get(typeNameParts.size() - 1);
      if (field.getType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        for (DescriptorProto nested : descriptor.getNestedTypeList()) {
          if (nested.getName().equals(typeName)) {
            alreadyPrinted.add(field.getTypeName());
            return printMessage(nested, prefix, packageName);
          }
        }
      } else if (field.getType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        for (EnumDescriptorProto nested : descriptor.getEnumTypeList()) {
          if (nested.getName().equals(typeName)) {
            alreadyPrinted.add(field.getTypeName());
            return printEnum(nested, prefix, packageName);
          }
        }
      }
    }
    // The type wasn't defined in this scope, print nothing.
    return "";
  }

  // Build the field.
  private String printField(FieldDescriptorProto field, String containingType, String indent) {
    StringBuilder message = new StringBuilder();

    // Add the "repeated" keyword, if necessary.
    message.append(indent);
    if (field.getLabel() == FieldDescriptorProto.Label.LABEL_REPEATED) {
      message.append("repeated ");
    }

    // Add the type of the field.
    if ((field.getType() == FieldDescriptorProto.Type.TYPE_MESSAGE
            || field.getType() == FieldDescriptorProto.Type.TYPE_ENUM)
        && field.hasTypeName()) {
      List<String> typeNameParts = Splitter.on('.').splitToList(field.getTypeName());
      List<String> containingTypeParts = Splitter.on('.').splitToList(containingType);
      int numCommon = 0;
      while (numCommon < typeNameParts.size() - 1
          && numCommon < containingTypeParts.size()
          && typeNameParts.get(numCommon).equals(containingTypeParts.get(numCommon))) {
        numCommon++;
      }
      message.append(Joiner.on('.').join(typeNameParts.subList(numCommon, typeNameParts.size())));
    } else if (field.getType().toString().startsWith("TYPE_")) {
      message.append(field.getType().toString().substring(5).toLowerCase());
    } else {
      message.append("INVALID_TYPE");
    }

    // Add the name and field number.
    message.append(" ").append(field.getName()).append(" = ").append(field.getNumber());

    FieldOptions options = field.getOptions();
    boolean hasFieldOption = false;
    if (options.hasExtension(Annotations.isChoiceType)) {
      hasFieldOption =
          addFieldOption(
              "(is_choice_type)",
              options.getExtension(Annotations.isChoiceType).toString(),
              hasFieldOption,
              message);
    }
    if (options.hasExtension(Annotations.validationRequirement)) {
      hasFieldOption =
          addFieldOption(
              "(validation_requirement)",
              options.getExtension(Annotations.validationRequirement).toString(),
              hasFieldOption,
              message);
    }
    if (field.hasJsonName()) {
      hasFieldOption =
          addFieldOption("json_name", "\"" + field.getJsonName() + "\"", hasFieldOption, message);
    }
    if (hasFieldOption) {
      message.append("]");
    }

    return message.append(";\n").toString();
  }

  private boolean addFieldOption(
      String option, String value, boolean hasFieldOption, StringBuilder message) {
    if (!hasFieldOption) {
      message.append(" [");
      hasFieldOption = true;
    } else {
      message.append(", ");
    }
    message.append(option).append(" = ").append(value);
    return true;
  }
}
