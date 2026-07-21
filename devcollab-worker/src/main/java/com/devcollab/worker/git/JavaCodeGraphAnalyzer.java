package com.devcollab.worker.git;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class JavaCodeGraphAnalyzer {

    private static final long MAX_JAVA_SOURCE_BYTES = 2L * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(
            JavaCodeGraphAnalyzer.class
    );

    public CodeGraphProjection analyze(
            Path repositoryRoot,
            List<GitRepositoryFileProjection> files
    ) {
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));
        List<ParsedSource> sources = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (GitRepositoryFileProjection file : files) {
            if (!"Java".equals(file.language())) {
                continue;
            }
            if (file.sizeBytes() > MAX_JAVA_SOURCE_BYTES) {
                failures.add(file.path() + ": source exceeds 2 MiB analysis limit");
                continue;
            }
            Path source = repositoryRoot.resolve(file.path()).normalize();
            if (!source.startsWith(repositoryRoot.normalize())) {
                failures.add(file.path() + ": path escapes repository root");
                continue;
            }
            try {
                var result = parser.parse(source);
                if (result.getResult().isEmpty()) {
                    failures.add(file.path() + ": " + problems(result.getProblems()));
                    continue;
                }
                if (!result.getProblems().isEmpty()) {
                    failures.add(file.path() + ": " + problems(result.getProblems()));
                }
                sources.add(new ParsedSource(file.path(), result.getResult().get()));
            } catch (Exception exception) {
                failures.add(file.path() + ": " + exception.getMessage());
            }
        }

        List<CodeSymbolProjection> symbols = new ArrayList<>();
        Map<String, TypeInfo> types = new HashMap<>();
        for (ParsedSource source : sources) {
            for (TypeDeclaration<?> type : source.unit().findAll(TypeDeclaration.class)) {
                String qualifiedName = qualifiedName(source.unit(), type);
                String symbolKey = typeSymbolKey(source.path(), qualifiedName);
                types.put(qualifiedName, new TypeInfo(
                        source.path(), symbolKey, qualifiedName, type
                ));
                symbols.add(typeSymbol(source.path(), symbolKey, qualifiedName, type));
                addMembers(symbols, source.path(), symbolKey, qualifiedName, type);
            }
        }

        Set<CodeFileDependencyProjection> fileDependencies = new LinkedHashSet<>();
        Set<CodeSymbolDependencyProjection> symbolDependencies =
                new LinkedHashSet<>();
        for (ParsedSource source : sources) {
            Map<String, String> explicitImports = explicitImports(source.unit());
            addImportDependencies(
                    source, types, explicitImports, fileDependencies
            );
            for (TypeDeclaration<?> declaration
                    : source.unit().findAll(TypeDeclaration.class)) {
                String qualifiedName = qualifiedName(source.unit(), declaration);
                TypeInfo sourceType = types.get(qualifiedName);
                if (sourceType == null) {
                    continue;
                }
                if (declaration instanceof ClassOrInterfaceDeclaration classType) {
                    addTypeRelations(sourceType, classType.getExtendedTypes(),
                            "EXTENDS", source.unit(), explicitImports, types,
                            symbolDependencies);
                    addTypeRelations(sourceType, classType.getImplementedTypes(),
                            "IMPLEMENTS", source.unit(), explicitImports, types,
                            symbolDependencies);
                } else if (declaration instanceof EnumDeclaration enumType) {
                    addTypeRelations(sourceType, enumType.getImplementedTypes(),
                            "IMPLEMENTS", source.unit(), explicitImports, types,
                            symbolDependencies);
                } else if (declaration instanceof RecordDeclaration recordType) {
                    addTypeRelations(sourceType, recordType.getImplementedTypes(),
                            "IMPLEMENTS", source.unit(), explicitImports, types,
                            symbolDependencies);
                }
            }
        }

        if (!failures.isEmpty()) {
            log.warn("Java code graph projection reported {} source problems: {}",
                    failures.size(), failures.stream().limit(10).toList());
        }
        return new CodeGraphProjection(
                List.copyOf(symbols), List.copyOf(symbolDependencies),
                List.copyOf(fileDependencies), List.copyOf(failures)
        );
    }

    private void addMembers(
            List<CodeSymbolProjection> symbols,
            String filePath,
            String parentKey,
            String typeName,
            TypeDeclaration<?> type
    ) {
        for (var member : type.getMembers()) {
            if (member instanceof MethodDeclaration method) {
                String signature = methodSignature(method);
                symbols.add(memberSymbol(
                        filePath, parentKey, typeName + "#" + method.getNameAsString(),
                        method.getNameAsString(), "METHOD", signature,
                        parentKey + "#" + signature,
                        method.getRange().map(range -> range.begin.line).orElse(null),
                        method.getRange().map(range -> range.end.line).orElse(null)
                ));
            } else if (member instanceof ConstructorDeclaration constructor) {
                String signature = constructorSignature(constructor);
                symbols.add(memberSymbol(
                        filePath, parentKey, typeName + "#<init>",
                        constructor.getNameAsString(), "CONSTRUCTOR", signature,
                        parentKey + "#" + signature,
                        constructor.getRange().map(range -> range.begin.line).orElse(null),
                        constructor.getRange().map(range -> range.end.line).orElse(null)
                ));
            } else if (member instanceof FieldDeclaration field) {
                for (var variable : field.getVariables()) {
                    String signature = variable.getTypeAsString() + " "
                            + variable.getNameAsString();
                    symbols.add(memberSymbol(
                            filePath, parentKey,
                            typeName + "#" + variable.getNameAsString(),
                            variable.getNameAsString(), "FIELD", signature,
                            parentKey + "#" + variable.getNameAsString(),
                            variable.getRange().map(range -> range.begin.line).orElse(null),
                            variable.getRange().map(range -> range.end.line).orElse(null)
                    ));
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CodeSymbolProjection typeSymbol(
            String filePath,
            String symbolKey,
            String qualifiedName,
            TypeDeclaration<?> type
    ) {
        String parent = type.findAncestor(TypeDeclaration.class)
                .map(parentType -> typeSymbolKey(filePath, qualifiedName(
                        type.findCompilationUnit().orElseThrow(), parentType
                ))).orElse(null);
        return new CodeSymbolProjection(
                filePath, symbolKey, "JAVA", typeKind(type), qualifiedName,
                type.getNameAsString(), typeKind(type) + " " + qualifiedName,
                parent,
                type.getRange().map(range -> range.begin.line).orElse(null),
                type.getRange().map(range -> range.end.line).orElse(null)
        );
    }

    private CodeSymbolProjection memberSymbol(
            String filePath,
            String parentKey,
            String qualifiedName,
            String simpleName,
            String kind,
            String signature,
            String key,
            Integer startLine,
            Integer endLine
    ) {
        return new CodeSymbolProjection(
                filePath, key, "JAVA", kind, qualifiedName, simpleName,
                signature, parentKey, startLine, endLine
        );
    }

    private void addImportDependencies(
            ParsedSource source,
            Map<String, TypeInfo> types,
            Map<String, String> explicitImports,
            Set<CodeFileDependencyProjection> dependencies
    ) {
        for (ImportDeclaration declaration : source.unit().getImports()) {
            if (declaration.isStatic()) {
                continue;
            }
            if (declaration.isAsterisk()) {
                // A wildcard import does not prove that every type in the package
                // is used. Keep the graph conservative until symbol resolution is
                // introduced.
                continue;
            } else {
                TypeInfo target = types.get(declaration.getNameAsString());
                if (target != null) {
                    addFileDependency(source.path(), target.path(), dependencies);
                }
            }
        }
    }

    private void addFileDependency(
            String source,
            String target,
            Set<CodeFileDependencyProjection> dependencies
    ) {
        if (!source.equals(target)) {
            dependencies.add(new CodeFileDependencyProjection(
                    source, target, "IMPORTS"
            ));
        }
    }

    private void addTypeRelations(
            TypeInfo source,
            List<ClassOrInterfaceType> targetTypes,
            String relation,
            CompilationUnit unit,
            Map<String, String> explicitImports,
            Map<String, TypeInfo> types,
            Set<CodeSymbolDependencyProjection> dependencies
    ) {
        for (ClassOrInterfaceType targetType : targetTypes) {
            resolveType(targetType.getNameWithScope(), unit, explicitImports, types)
                    .ifPresent(target -> dependencies.add(
                            new CodeSymbolDependencyProjection(
                                    source.symbolKey(), target.symbolKey(), relation,
                                    source.path()
                            )
                    ));
        }
    }

    private Optional<TypeInfo> resolveType(
            String name,
            CompilationUnit unit,
            Map<String, String> explicitImports,
            Map<String, TypeInfo> types
    ) {
        TypeInfo exact = types.get(name);
        if (exact != null) {
            return Optional.of(exact);
        }
        String imported = explicitImports.get(simpleName(name));
        if (imported != null && types.containsKey(imported)) {
            return Optional.of(types.get(imported));
        }
        String packageName = unit.getPackageDeclaration()
                .map(value -> value.getNameAsString()).orElse("");
        String samePackage = packageName.isBlank() ? name : packageName + "." + name;
        return Optional.ofNullable(types.get(samePackage));
    }

    private Map<String, String> explicitImports(CompilationUnit unit) {
        Map<String, String> result = new HashMap<>();
        unit.getImports().stream()
                .filter(value -> !value.isStatic() && !value.isAsterisk())
                .forEach(value -> result.put(
                        simpleName(value.getNameAsString()), value.getNameAsString()
                ));
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String qualifiedName(CompilationUnit unit, TypeDeclaration<?> type) {
        List<String> names = new ArrayList<>();
        TypeDeclaration<?> current = type;
        names.add(current.getNameAsString());
        while (current.findAncestor(TypeDeclaration.class).isPresent()) {
            current = current.findAncestor(TypeDeclaration.class).orElseThrow();
            names.addFirst(current.getNameAsString());
        }
        String localName = String.join(".", names);
        return unit.getPackageDeclaration()
                .map(value -> value.getNameAsString() + "." + localName)
                .orElse(localName);
    }

    private String typeKind(TypeDeclaration<?> type) {
        if (type instanceof AnnotationDeclaration) return "ANNOTATION";
        if (type instanceof EnumDeclaration) return "ENUM";
        if (type instanceof RecordDeclaration) return "RECORD";
        if (type instanceof ClassOrInterfaceDeclaration value) {
            return value.isInterface() ? "INTERFACE" : "CLASS";
        }
        return "TYPE";
    }

    private String methodSignature(MethodDeclaration method) {
        return method.getNameAsString() + "(" + method.getParameters().stream()
                .map(parameter -> parameter.getTypeAsString())
                .reduce((left, right) -> left + "," + right).orElse("") + ")";
    }

    private String constructorSignature(ConstructorDeclaration constructor) {
        return "<init>(" + constructor.getParameters().stream()
                .map(parameter -> parameter.getTypeAsString())
                .reduce((left, right) -> left + "," + right).orElse("") + ")";
    }

    static String typeSymbolKey(String filePath, String qualifiedName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    filePath.getBytes(StandardCharsets.UTF_8)
            );
            String pathDigest = java.util.HexFormat.of()
                    .formatHex(digest).substring(0, 16);
            return "java:" + qualifiedName + "@" + pathDigest;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String simpleName(String qualifiedName) {
        int separator = qualifiedName.lastIndexOf('.');
        return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
    }

    private String problems(List<com.github.javaparser.Problem> problems) {
        return problems.stream().limit(3)
                .map(com.github.javaparser.Problem::getMessage)
                .reduce((left, right) -> left + "; " + right)
                .orElse("unknown parse problem");
    }

    private record ParsedSource(String path, CompilationUnit unit) {
    }

    private record TypeInfo(
            String path,
            String symbolKey,
            String qualifiedName,
            TypeDeclaration<?> declaration
    ) {
    }
}
