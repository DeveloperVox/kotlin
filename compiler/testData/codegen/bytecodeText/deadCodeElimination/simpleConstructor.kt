class A
fun box() {
    val x: A? = A()
    
    var y: A?
    if (1 == 1) {
        y = x 
    } 
    else {
        y = null        
    }
    
    val z: A? = A()
    
    val z1: A? = if (1 == 1) z else x
    
    x!!
    y!!
    z!!
    z1!!
    
}

// 0 IFNULL
// 1 IFNONNULL
// 1 throwNpe
// 0 ATHROW
