// The MIT License (MIT)
//
// Copyright (c) 2015 Arian Fornaris
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to permit
// persons to whom the Software is furnished to do so, subject to the
// following conditions: The above copyright notice and this permission
// notice shall be included in all copies or substantial portions of the
// Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
// OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
// NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
// DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
// OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
// USE OR OTHER DEALINGS IN THE SOFTWARE.
package phasereditor.inspect.core.build;

import static java.lang.System.out;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import phasereditor.inspect.core.InspectCore;
import phasereditor.inspect.core.jsdoc.PhaserConstant;
import phasereditor.inspect.core.jsdoc.PhaserJSDoc;
import phasereditor.inspect.core.jsdoc.PhaserMethod;
import phasereditor.inspect.core.jsdoc.PhaserType;
import phasereditor.inspect.core.jsdoc.PhaserVariable;

public class BuildPhaserAPI {
	private static PhaserJSDoc _phaserJSDoc;

	public static void main(String[] args) throws IOException {
		Path wsPath = Paths.get(".").toAbsolutePath().getParent().getParent();
		Path projectPath = wsPath.resolve(InspectCore.RESOURCES_PLUGIN_ID);
		_phaserJSDoc = new PhaserJSDoc(projectPath.resolve("phaser-master/src"),
				projectPath.resolve("phaser-custom/jsdoc/docs.json"));

		PhaserType[] types = _phaserJSDoc.getTypes().stream().toArray(PhaserType[]::new);

		// do a brute and unstable sort, the java merge sort protocol is
		// violated.
		for (int i = 0; i < types.length - 1; i++) {
			for (int j = i + 1; j < types.length; j++) {
				PhaserType a = types[i];
				PhaserType b = types[j];
				int c = sortType(a, b);
				if (c > 0) {
					types[i] = b;
					types[j] = a;
				}
			}
		}

		StringBuilder sb = new StringBuilder();

		HashSet<String> ignore = new HashSet<>();
		ignore.add("Phaser.Easing");

		for (PhaserType type : types) {
			if (ignore.contains(type.getName())) {
				continue;
			}

			String typeName = getTypeName(type);
			{
				StringBuilder params = new StringBuilder();
				int i = 0;
				for (PhaserVariable var : type.getConstructorArgs()) {
					if (i > 0) {
						params.append(", ");
					}
					String paramName = getValidName(var.getName());

					params.append(paramName);
					i++;
				}

				sb.append("var " + typeName + " = function (" + params + ") {};\n");
			}

			// extends

			if (type.getExtends() != null) {
				String base = "Object";
				if (type.getExtends().size() == 1) {
					base = type.getExtends().get(0);
				}
				sb.append(typeName + ".prototype = new " + base + "();\n");
			}

			// constants

			for (PhaserVariable var : type.getConstants()) {
				String varName = getValidName(var.getName());
				String varType = getVariableTypeName(var.getTypes());
				sb.append(typeName + "." + varName + " = " + getVarDeclExpression(varType) + ";\n");
			}

			// properties

			for (PhaserVariable var : type.getProperties()) {
				String varName = getValidName(var.getName());
				String varType = getVariableTypeName(var.getTypes());
				sb.append(typeName + ".prototype." + varName + " = " + getVarDeclExpression(varType) + ";\n");
			}

			// methods

			for (PhaserMethod method : type.getMethods()) {
				String methodName = method.getName();
				String returnType = getMethodTypeName(method.getReturnTypes());

				StringBuilder params = new StringBuilder();
				int i = 0;
				for (PhaserVariable var : method.getArgs()) {
					if (i > 0) {
						params.append(", ");
					}
					String paramName = getValidName(var.getName());

					params.append(paramName);
					i++;
				}

				sb.append(typeName + ".prototype." + methodName + " = function (" + params + ") {");
				if (!returnType.equals("void")) {
					sb.append(" return new " + returnType + "(); ");
				}
				sb.append("};\n");
			}

			sb.append("\n");
		}

		// name spaces

		Set<String> namespaces = new HashSet<>();

		sb.append("\n\n");

		for (PhaserType type : types) {
			if (ignore.contains(type.getName())) {
				continue;
			}
			String name = type.getName();

			String[] elems = name.split("\\.");
			String namespace = "";
			for (int i = 0; i < elems.length - 1; i++) {
				if (namespace.length() > 0) {
					namespace += ".";
				}
				String elem = elems[i];
				namespace += elem;
				if (!namespaces.contains(namespace)) {
					namespaces.add(namespace);
					sb.append(namespace + " = {};\n");
				}
			}
		}

		// easing
		String[] easings = { "Quadratic", "Cubic", "Quartic", "Quintic", "Sinusoidal", "Exponential", "Circular",
				"Elastic", "Back", "Bounce" };

		sb.append("\n// easing\n\n");
		sb.append("\nPhaser.Easing = {};\n");

		sb.append("Phaser.Easing.Linear = {};\n");
		sb.append("Phaser.Easing.Linear.None = function (k) { return new Number(); };\n");

		for (String easing : easings) {
			sb.append("Phaser.Easing." + easing + " = {};\n");
			for (String inOut : new String[] { "In", "Out", "InOut" }) {
				sb.append("Phaser.Easing." + easing + "." + inOut + " = function (k) { return new Number(); };\n");
			}
		}

		sb.append(
				"Phaser_Easing.Default = Phaser_Easing.Linear.None;\nPhaser_Easing.Power0 = Phaser_Easing.Linear.None;\nPhaser_Easing.Power1 = Phaser_Easing.Quadratic.Out;\nPhaser_Easing.Power2 = Phaser_Easing.Cubic.Out;\nPhaser_Easing.Power3 = Phaser_Easing.Quartic.Out;\nPhaser_Easing.Power4 = Phaser_Easing.Quintic.Out;");

		// alias
		sb.append("\n\n// alias\n\n");

		for (PhaserType type : types) {
			String name = type.getName();
			sb.append(name + " = " + getTypeName(type) + ";\n");
		}

		// global constants
		sb.append("\n");
		sb.append("// Global constants\n");
		sb.append("\n");

		List<PhaserConstant> globalConstants = _phaserJSDoc.getGlobalConstants();
		for (PhaserConstant cons : globalConstants) {
			String varName = getValidName(cons.getName());
			String varType = getVariableTypeName(cons.getTypes());

			if (varType.equals("Object")) {
				// FIXME: this is the case of blendMode and
				// scaleMode, they need a special case.
				continue;
			}

			// sb.append("Phaser." + varName + " = new " + varType + "();\n");
			sb.append("Phaser." + varName + " = " + varType + ";\n");
		}
		sb.append("\n");

		Path phaserApiConcat = projectPath.resolve("phaser-custom/api/phaser-api-concat.js");
		sb.append(new String(Files.readAllBytes(phaserApiConcat)));

		out.println((sb.length() / 1024) + "kb");

		Path phaserApi = projectPath.resolve("phaser-custom/api/phaser-api.js");
		Files.write(phaserApi, sb.toString().getBytes());

	}

	/**
	 * @param varType
	 * @return
	 */
	private static String getVarDeclExpression(String varType) {
		switch (varType) {
		case "Boolean":
			return "true";
		case "Number":
			return "0";
		case "String":
			return "\"\"";
		default:
			break;
		}
		
		// this is the case of the Array(DisplayObject), that is converted to [new DisplayObject()]
		if (varType.startsWith("[new")) {
			return varType;
		}
		
		return "new " + varType + "()";
	}

	static int sortType(PhaserType a, PhaserType b) {

		int a1 = a.getName().startsWith("PIXI") ? 0 : 1;
		int b1 = b.getName().startsWith("PIXI") ? 0 : 1;

		String a1_using = getReferencedTypes(a);
		String b1_using = getReferencedTypes(b);

		if (a1_using.contains(b.getName())) {
			a1 += 10;
		}

		if (b1_using.contains(a.getName())) {
			b1 += 10;
		}

		return a1 - b1;
	}

	private static String getReferencedTypes(PhaserType type) {
		StringBuilder sb = new StringBuilder();

		sb.append(type.getExtends() == null ? "" : type.getExtends() + " ");

		for (PhaserVariable var : type.getProperties()) {
			for (String name : var.getTypes()) {
				sb.append(name + " ");
			}
		}

		for (PhaserVariable var : type.getConstants()) {
			for (String name : var.getTypes()) {
				sb.append(name + " ");
			}
		}

		for (PhaserMethod method : type.getMethods()) {
			for (String name : method.getReturnTypes()) {
				sb.append(name + " ");
			}
		}

		return sb.toString();
	}

	private static String getValidName(String name) {
		String validName = "";

		for (char c : name.toCharArray()) {
			if (Character.isJavaIdentifierPart(c)) {
				validName += c;
			} else {
				validName += "_";
			}
		}
		if (validName.length() == 0) {
			validName = "guess";
		}

		// test javascript keywords
		if (validName.equals("if")) {
			return "_if";
		}

		return validName;
	}

	private static String getMethodTypeName(String[] types) {
		if (types.length == 0) {
			return "void";
		}

		return getVariableTypeName(types);
	}

	public static String getVariableTypeName(String[] types) {
		if (types.length == 0) {
			return "Object";
		}
		String name = types[0];

		if (name.startsWith("Array(") && name.endsWith(")")) {
			String elemType = name.substring(6, name.length() - 1);
			
			// a patch to the rule because the worths it 
			if (elemType.equals("DisplayObject")) {
				elemType = "PIXI_DisplayObject";
			}
			return "[new " + elemType + "()]";
			
		}

		if (name.toLowerCase().startsWith("array")) {
			return "Array";
		}

		switch (name) {
		case "string":
			return "String";
		case "integer":
			return "Number";
		case "number":
			return "Number";
		case "boolean":
			return "Boolean";
		case "object":
		case "any":
			return "Object";
		case "array":
			return "Array";
		case "function":
			return "Function";
		default:
			break;
		}

		return getTypeName(name);
	}

	public static String getTypeName(PhaserType type) {
		String name = type.getName();
		return getTypeName(name);
	}

	private static String getTypeName(String name) {
		// check the cases where is missing the namespace, like in Point that
		// should be Phaser.Point.
		Map<String, PhaserType> map = _phaserJSDoc.getTypesMap();
		if (!map.containsKey(name)) {
			if (map.containsKey("Phaser." + name)) {
				return "Phaser_" + name;
			}
			if (map.containsKey("PIXI." + name)) {
				return "PIXI_" + name;
			}
		}

		return getValidName(name.replace(".", "_"));
	}
}
