/*
 * Copyright 2010-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.descriptors.serialization.context

import org.jetbrains.jet.storage.StorageManager
import org.jetbrains.jet.lang.descriptors.*
import org.jetbrains.jet.descriptors.serialization.*
import org.jetbrains.jet.descriptors.serialization.descriptors.AnnotationLoader
import org.jetbrains.jet.descriptors.serialization.descriptors.ConstantLoader
import org.jetbrains.jet.lang.resolve.name.ClassId

public class DeserializationComponents(
        public val storageManager: StorageManager,
        public val moduleDescriptor: ModuleDescriptor,
        public val classDataFinder: ClassDataFinder,
        public val annotationLoader: AnnotationLoader,
        public val constantLoader: ConstantLoader,
        public val packageFragmentProvider: PackageFragmentProvider,
        public val flexibleTypeCapabilitiesDeserializer: FlexibleTypeCapabilitiesDeserializer
) {
    public val classDeserializer: ClassDeserializer = ClassDeserializer(this)

    public fun deserializeClass(classId: ClassId): ClassDescriptor? = classDeserializer.deserializeClass(classId)

    public fun createContext(descriptor: PackageFragmentDescriptor, nameResolver: NameResolver): DeserializationContext =
            DeserializationContext(this, nameResolver, descriptor, parentTypeDeserializer = null, typeParameters = listOf())
}


public class DeserializationContext(
        public val components: DeserializationComponents,
        public val nameResolver: NameResolver,
        public val containingDeclaration: DeclarationDescriptor,
        parentTypeDeserializer: TypeDeserializer?,
        typeParameters: List<ProtoBuf.TypeParameter>
) {
    val typeDeserializer = TypeDeserializer(this, parentTypeDeserializer, typeParameters,
                                            "Deserializer for ${containingDeclaration.getName()}")

    val memberDeserializer = MemberDeserializer(this)

    val storageManager: StorageManager get() = components.storageManager

    fun childContext(
            descriptor: DeclarationDescriptor,
            typeParameterProtos: List<ProtoBuf.TypeParameter>,
            nameResolver: NameResolver = this.nameResolver
    ) = DeserializationContext(
            components, nameResolver, descriptor, parentTypeDeserializer = this.typeDeserializer, typeParameters = typeParameterProtos
    )
}
