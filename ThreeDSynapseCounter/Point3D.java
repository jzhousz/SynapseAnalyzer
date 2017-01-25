
//helper structure to facilitate manipulation of 3D points
class Point3D
{
	int x, y, z;
	
	//constructor
	Point3D(int x, int y, int z) {this.x=x; this.y=y; this.z=z;}
	
	//Return true if 2 points are close enough
	@Override
	public boolean equals(Object p2)
	{ 
		int distx, disty, distz;
		//this is super misleading, doesnt equate points at all! -Jon Sanders 12/15
		/*if (x>p2.x) distx = x-p2.x; else distx = p2.x -x;
		if (y>p2.y) disty = y-p2.y; else disty = p2.y -y;
		if (z>p2.z) distz = z-p2.z; else distz = p2.z -z;
		//distance on xy plane should be smaller than 2, on z should be smaller than 1. - Due to different resolution, don't say distx+disty+distz < 3
		if((distx<2) && (disty<=2) && distz <=1) return true;
		*/
		if(p2 instanceof Point3D ) {
			Point3D p = (Point3D) p2;
			if( (x == p.x) && (y == p.y) && (z == p.z)) return true;
		}
		
		return false; 
	}

	//neighborhood check to see if a point is within a region near this point
	public boolean closesTo(Point3D p2, int dx, int dy, int dz)
	{ 
		int distx, disty, distz;
		if (x>p2.x) distx = x-p2.x; else distx = p2.x -x;
		if (y>p2.y) disty = y-p2.y; else disty = p2.y -y;
		if (z>p2.z) distz = z-p2.z; else distz = p2.z -z;
		//distance on xy plane should be smaller than 2, on z should be smaller than 1. - Due to different resolution, don't say distx+disty+distz < 3
		if((distx <= dx) && (disty <= dy) && distz <= dz) return true;
		else return false; 
	}

	//return the point as a string 'x,y,z'
	String convertToString() {
	
		return (""+x+","+y+","+z);
	
	}
	
	//offset the point by any X,Y,Z
	void offsetPoint(int offX, int offY, int offZ) {
		x += offX;
		y += offY;
		z += offZ;
	}
	//custom hash NEEDED when override equals
	@Override
	public int hashCode() {
		int hash = 17;
		hash = hash*31 + z;
		hash = hash*13 + y;
		hash = hash*23 + x;
		return hash;
	}
	
}

